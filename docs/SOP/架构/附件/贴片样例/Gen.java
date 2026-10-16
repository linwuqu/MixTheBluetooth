import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 贴片样例码生成器 —— 一次性工具,不进 App。
 *
 * 用 zxing-core 的 DataMatrixWriter 编 Data Matrix ECC200,自己画 PNG(不依赖 zxing:javase)。
 * 载荷格式见 docs/SOP/架构/13-贴片扫码绑定.md 的 D1:文本,字段集合对齐 V1 编码协议的 01-07。
 *
 * 用法(在本目录下):
 *   curl -o /tmp/zxing-core.jar https://maven.aliyun.com/repository/public/com/google/zxing/core/3.5.3/core-3.5.3.jar
 *   java -cp /tmp/zxing-core.jar Gen.java
 *
 * 出三样东西:
 *   投屏A..F.png   一张一枚、四周留白,全屏显示给手机扫
 *   扫码页.html    六枚轮播,按 1-6 切换 —— 投屏扫就开这个,省得来回换文件
 *   贴片样例码.png 一页六枚,打印用
 *
 * 末尾自带回读验证:把写出的 PNG 重新解码,逐字节比对载荷。写错了会在这里炸,不会等到真机上才发现。
 */
public class Gen {

    /** 投屏图每模块的像素数。够大到 1080p 全屏时每模块约 3mm。 */
    private static final int SIZE_SINGLE = 16;

    /** 打印页每模块的像素数。12px 打在 16mm 码区里约 840dpi,够了。 */
    private static final int SIZE_SHEET = 12;

    /** 四周白边。Data Matrix 的最小白边是 1 个模块,贴片印刷规格按 2X 留。 */
    private static final int QUIET = 2;

    /** 投屏图四周再留一圈白。屏幕边框、反光都在这圈里消化掉。 */
    private static final int MARGIN = 80;

    private record Sample(String id, String payload, String expect) {}

    /**
     * 六枚样例:前三枚该成功(验 1:n 与短号分辨),后三枚该被拒绝(验错误路径说得出原因)。
     * 前三枚的 patchId 前 8 位互不相同,扫完看短号就能分辨。
     */
    private static final Sample[] SAMPLES = {
        new Sample("A", patch("550e8400-e29b-41d4-a716-446655440000", "DEMO-260701"),
            "绑定成功,单元标题右侧出现「贴片 550e8400」"),
        new Sample("B", patch("7c9e6679-7425-40de-944b-e07fc1f90ae7", "DEMO-260702"),
            "变成「贴片 7c9e6679 · 共 2 枚」"),
        new Sample("C", patch("3f2504e0-4f89-11d3-9a0c-0305e82c3301", "DEMO-260703"),
            "变成「共 3 枚」"),
        new Sample("D", "HELLO-DEMO-123456",
            "扫码页不关,底部红条:「这不是贴片码」"),
        new Sample("E", "CGPATCH/2|patchId=550e8400-e29b-41d4-a716-446655440000",
            "「贴片码版本 2 暂不支持,当前 App 只认 1」"),
        new Sample("F", "CGPATCH/1|model=1|lot=DEMO-260799",
            "「贴片码里没有编号」"),
    };

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        if (!dir.isDirectory()) throw new IllegalArgumentException("不是目录: " + dir);

        // 强制方形符号:不加的话短载荷会被编成 8×18 这类长方形,和贴片规格(40×40/44×44)对不上
        Map<EncodeHintType, Object> enc = new EnumMap<>(EncodeHintType.class);
        enc.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);

        BitMatrix[] codes = new BitMatrix[SAMPLES.length];
        for (int i = 0; i < SAMPLES.length; i++) {
            codes[i] = new DataMatrixWriter()
                .encode(SAMPLES[i].payload(), BarcodeFormat.DATA_MATRIX, 0, 0, enc);
        }

        List<byte[]> singlePngs = new ArrayList<>();
        for (int i = 0; i < SAMPLES.length; i++) {
            BufferedImage img = render(codes[i], SIZE_SINGLE, MARGIN);
            byte[] png = toPng(img);
            singlePngs.add(png);
            write(new File(dir, "投屏" + SAMPLES[i].id() + ".png"), png);
        }

        write(new File(dir, "扫码页.html"), viewer(singlePngs).getBytes(StandardCharsets.UTF_8));
        write(new File(dir, "贴片样例码.png"), toPng(sheet(codes).image()));

        verify(dir, singlePngs);
    }

    // ---------- 载荷 ----------

    private static String patch(String patchId, String lot) {
        return "CGPATCH/1"
            + "|patchId=" + patchId
            + "|model=1"
            + "|lot=" + lot
            + "|mfg=2026-07-01"
            + "|exp=2027-07-01"
            + "|maxUseMin=120"
            + "|calRef=DEMO-CAL";
    }

    // ---------- 画图 ----------

    /** 一枚码渲染成方形图:码居中,四周 MARGIN 像素 + QUIET 个模块的白。 */
    private static BufferedImage render(BitMatrix m, int moduleSize, int margin) {
        int w = (m.getWidth() + QUIET * 2) * moduleSize + margin * 2;
        int h = (m.getHeight() + QUIET * 2) * moduleSize + margin * 2;
        int side = Math.max(w, h);
        BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, side, side);
        drawMatrix(g, m,
            (side - m.getWidth() * moduleSize) / 2,
            (side - m.getHeight() * moduleSize) / 2,
            moduleSize);
        g.dispose();
        return img;
    }

    /** 打印页,连同每枚码在页上的位置(含白边),验证时按块裁下来解。 */
    private record Sheet(BufferedImage image, List<Rect> codeAreas) {}

    private record Rect(int x, int y, int w, int h) {}

    /** 一页六枚,两列三行,每枚带编号与预期结果 —— 打印出来照着扫。 */
    private static Sheet sheet(BitMatrix[] codes) {
        int cols = 2;
        int rows = (codes.length + cols - 1) / cols;
        int labelH = 30, expectH = 24, pad = 32;

        int cellW = 0, cellCodeH = 0;
        for (BitMatrix m : codes) {
            cellW = Math.max(cellW, (m.getWidth() + QUIET * 2) * SIZE_SHEET);
            cellCodeH = Math.max(cellCodeH, (m.getHeight() + QUIET * 2) * SIZE_SHEET);
        }
        cellW += pad * 2;
        int cellH = labelH + expectH + cellCodeH + pad * 2;

        int width = cellW * cols;
        int height = cellH * rows;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        List<Rect> areas = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            int cx = (i % cols) * cellW;
            int cy = (i / cols) * cellH;
            BitMatrix m = codes[i];
            int px = (m.getWidth() + QUIET * 2) * SIZE_SHEET;
            int py = (m.getHeight() + QUIET * 2) * SIZE_SHEET;

            g.setColor(new Color(0xBB, 0xBB, 0xBB));
            g.drawRect(cx + 1, cy + 1, cellW - 2, cellH - 2);

            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.drawString("样例 " + SAMPLES[i].id()
                + "   patchId 前 8 位 " + shortId(SAMPLES[i]), cx + pad, cy + pad + 18);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(new Color(0x55, 0x55, 0x55));
            g.drawString("预期:" + SAMPLES[i].expect(), cx + pad, cy + pad + labelH + 12);

            int codel = cx + (cellW - px) / 2;
            int codet = cy + pad + labelH + expectH;
            areas.add(new Rect(codel, codet, px, py));
            drawMatrix(g, m,
                codel + QUIET * SIZE_SHEET,
                codet + QUIET * SIZE_SHEET,
                SIZE_SHEET);
        }
        g.dispose();
        return new Sheet(img, areas);
    }

    private static void drawMatrix(Graphics2D g, BitMatrix m, int left, int top, int size) {
        g.setColor(Color.BLACK);
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (m.get(x, y)) g.fillRect(left + x * size, top + y * size, size, size);
            }
        }
    }

    /** 从载荷里抠出 patchId 的前 8 位;没有 patchId(样例 D/F)就写个横线。 */
    private static String shortId(Sample s) {
        for (String seg : s.payload().split("\\|")) {
            if (seg.startsWith("patchId=")) {
                String id = seg.substring("patchId=".length());
                return id.length() <= 8 ? id : id.substring(0, 8);
            }
        }
        return "—";
    }

    // ---------- 投屏页 ----------

    /** 单文件 HTML:六枚码内嵌成 data URI,按 1-6 或左右键切换,全屏给手机扫。 */
    private static String viewer(List<byte[]> pngs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"zh-CN\">\n<head>\n")
            .append("<meta charset=\"utf-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            .append("<title>贴片样例码 · 投屏</title>\n")
            .append("<style>\n")
            .append("* { box-sizing: border-box; }\n")
            .append("html, body { margin: 0; height: 100%; background: #fff; }\n")
            .append("body { display: flex; flex-direction: column; align-items: center;\n")
            .append("  font: 15px/1.4 -apple-system, \"Segoe UI\", \"Microsoft YaHei\", sans-serif;\n")
            .append("  color: #111; }\n")
            .append("header { width: 100%; padding: 10px 16px; display: flex; gap: 8px;\n")
            .append("  align-items: center; justify-content: center; flex-wrap: wrap;\n")
            .append("  border-bottom: 1px solid #eee; }\n")
            .append("button { font: inherit; padding: 6px 14px; border: 1px solid #ccc;\n")
            .append("  border-radius: 8px; background: #fff; cursor: pointer; }\n")
            .append("button[aria-pressed=\"true\"] { background: #111; color: #fff;\n")
            .append("  border-color: #111; }\n")
            .append("main { flex: 1; min-height: 0; width: 100%; display: flex;\n")
            .append("  align-items: center; justify-content: center; padding: 8px; }\n")
            .append("img { max-width: 100%; max-height: 100%; image-rendering: pixelated;\n")
            .append("  -ms-interpolation-mode: nearest-neighbor; }\n")
            // 同屏视图:#grid 的 display 会盖掉 [hidden] 的 UA 样式,所以得自己再写一条
            .append("#grid { flex: 1; min-height: 0; width: 100%; display: grid;\n")
            .append("  grid-template-columns: repeat(3, 1fr); grid-template-rows: repeat(2, 1fr);\n")
            .append("  gap: 12px; }\n")
            .append("#grid[hidden] { display: none; }\n")
            .append("#grid figure { margin: 0; min-width: 0; min-height: 0; display: flex;\n")
            .append("  flex-direction: column; align-items: center; gap: 4px; }\n")
            .append("#grid img { flex: 1 1 auto; min-height: 0; width: 100%; height: 0;\n")
            .append("  object-fit: contain; }\n")
            .append("#grid figcaption { flex: 0 0 auto; font-size: 12px; color: #666; }\n")
            .append("footer { width: 100%; padding: 10px 16px; text-align: center;\n")
            .append("  color: #444; border-top: 1px solid #eee; }\n")
            .append("kbd { font: 12px ui-monospace, Consolas, monospace; padding: 1px 5px;\n")
            .append("  border: 1px solid #ddd; border-radius: 4px; background: #fafafa; }\n")
            .append("</style>\n</head>\n<body>\n<header id=\"tabs\"></header>\n")
            .append("<main><img id=\"code\" alt=\"贴片样例码\">")
            .append("<div id=\"grid\" hidden></div></main>\n")
            .append("<footer id=\"expect\"></footer>\n<script>\n")
            .append("const CODES = [\n");
        for (int i = 0; i < SAMPLES.length; i++) {
            sb.append("  { id: ").append(js(SAMPLES[i].id()))
                .append(", expect: ").append(js(SAMPLES[i].expect()))
                .append(", src: \"data:image/png;base64,")
                .append(Base64.getEncoder().encodeToString(pngs.get(i)))
                .append("\" },\n");
        }
        sb.append("];\n")
            .append("let at = 0;\n")
            .append("const tabs = document.getElementById('tabs');\n")
            .append("const img = document.getElementById('code');\n")
            .append("const grid = document.getElementById('grid');\n")
            .append("const expect = document.getElementById('expect');\n")
            .append("CODES.forEach((c, i) => {\n")
            .append("  const b = document.createElement('button');\n")
            .append("  b.textContent = (i + 1) + ' · 样例 ' + c.id;\n")
            .append("  b.onclick = () => show(i);\n")
            .append("  tabs.appendChild(b);\n")
            .append("  const fig = document.createElement('figure');\n")
            .append("  const gi = document.createElement('img');\n")
            .append("  gi.src = c.src; gi.alt = '样例 ' + c.id;\n")
            .append("  const cap = document.createElement('figcaption');\n")
            .append("  cap.textContent = '样例 ' + c.id + ' · ' + c.expect;\n")
            .append("  fig.append(gi, cap);\n")
            .append("  grid.appendChild(fig);\n")
            .append("});\n")
            .append("const all = document.createElement('button');\n")
            .append("all.textContent = '0 · 六枚同屏';\n")
            .append("all.onclick = () => showAll();\n")
            .append("tabs.appendChild(all);\n")
            .append("function mark(active) {\n")
            .append("  [...tabs.children].forEach(b =>\n")
            .append("    b.setAttribute('aria-pressed', String(b === active)));\n")
            .append("}\n")
            .append("function show(i) {\n")
            .append("  at = (i + CODES.length) % CODES.length;\n")
            .append("  grid.hidden = true;\n")
            .append("  img.hidden = false;\n")
            .append("  img.src = CODES[at].src;\n")
            .append("  expect.textContent = '样例 ' + CODES[at].id + ' 预期:' + CODES[at].expect;\n")
            .append("  mark(tabs.children[at]);\n")
            .append("}\n")
            .append("function showAll() {\n")
            .append("  img.hidden = true;\n")
            .append("  grid.hidden = false;\n")
            .append("  expect.textContent = '六枚同屏 —— 每枚该出现一个框,点哪枚绑哪枚;点空白处只是对焦,不会绑定';\n")
            .append("  mark(all);\n")
            .append("}\n")
            .append("addEventListener('keydown', e => {\n")
            .append("  if (e.key === '0' || e.key === 'g') { showAll(); return; }\n")
            .append("  const n = '123456'.indexOf(e.key);\n")
            .append("  if (n >= 0 && n < CODES.length) show(n);\n")
            .append("  else if (e.key === 'ArrowRight' || e.key === ' ') show(at + 1);\n")
            .append("  else if (e.key === 'ArrowLeft') show(at - 1);\n")
            .append("});\n")
            .append("show(0);\n")
            .append("</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static String js(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- 回读验证 ----------

    /**
     * 把写出的 PNG 重新解码,和载荷逐字节比对。
     *
     * 生成器只负责写,验证放这里是为了不漏:码印歪一点、白边不够、ImageIO 有损,
     * 都会在这一步露出来 —— 而不是等真机上扫不出来再回头怀疑 App。
     */
    private static void verify(File dir, List<byte[]> singles) throws Exception {
        // 不加 PURE_BARCODE:那是"图里只有码、没有边"时的捷径,而样例图是有白边的。
        // 走常规检测路径也更接近真机 —— App 那边同样不假设画面干净。
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        for (int i = 0; i < SAMPLES.length; i++) {
            String got;
            try {
                got = decode(readImage(new File(dir, "投屏" + SAMPLES[i].id() + ".png")), hints);
            } catch (Exception e) {
                throw new IllegalStateException("样例 " + SAMPLES[i].id() + " 解不出来: " + e, e);
            }
            check(SAMPLES[i].id(), SAMPLES[i].payload(), got);
            if (singles.get(i).length == 0) throw new IllegalStateException("空 PNG");
        }

        // 打印页按块裁开逐枚解,不用整页多码识别:后者要在一张图上同时找六个,
        // 检测器挑错区域就全军覆没,而人扫打印页时本来也是一枚一枚对准的。
        Sheet parsed = parseSheet(dir);
        for (int i = 0; i < SAMPLES.length; i++) {
            Rect r = parsed.codeAreas().get(i);
            BufferedImage crop = parsed.image()
                .getSubimage(r.x(), r.y(), r.w(), r.h());
            check(SAMPLES[i].id(), SAMPLES[i].payload(), decode(crop, hints));
        }

        System.out.println("回读验证通过:" + SAMPLES.length + " 枚单张 + 打印页 "
            + SAMPLES.length + " 枚,载荷逐字节一致");
    }

    /**
     * 打印页只有写出来的 PNG 是权威的,块位置得从生成时的同一套算式再算一遍。
     * 所以这里重跑一次 [sheet] —— 位置算法只有一处,改了不会两边对不上。
     */
    private static Sheet parseSheet(File dir) throws Exception {
        Map<EncodeHintType, Object> enc = new EnumMap<>(EncodeHintType.class);
        enc.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);
        BitMatrix[] codes = new BitMatrix[SAMPLES.length];
        for (int i = 0; i < SAMPLES.length; i++) {
            codes[i] = new DataMatrixWriter()
                .encode(SAMPLES[i].payload(), BarcodeFormat.DATA_MATRIX, 0, 0, enc);
        }

        Sheet planned = sheet(codes);
        BufferedImage written = readImage(new File(dir, "贴片样例码.png"));
        if (planned.image().getWidth() != written.getWidth()
            || planned.image().getHeight() != written.getHeight()) {
            throw new IllegalStateException("打印页尺寸与生成时对不上,块位置不可信");
        }
        return new Sheet(written, planned.codeAreas());
    }

    private static void check(String id, String want, String got) {
        if (!want.equals(got)) {
            throw new IllegalStateException(
                "样例 " + id + " 解回来对不上\n  期望: " + want + "\n  实际: " + got);
        }
    }

    private static BufferedImage readImage(File f) throws Exception {
        BufferedImage img = ImageIO.read(f);
        if (img == null) throw new IllegalStateException("读不出图: " + f);
        return img;
    }

    private static LuminanceSource source(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        return new RGBLuminanceSource(w, h, img.getRGB(0, 0, w, h, null, 0, w));
    }

    private static String decode(BufferedImage img, Map<DecodeHintType, Object> hints)
        throws Exception {
        Result r = new MultiFormatReader()
            .decode(new BinaryBitmap(new HybridBinarizer(source(img))), hints);
        return r.getText();
    }

    // ---------- 落盘 ----------

    private static byte[] toPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return out.toByteArray();
    }

    private static void write(File f, byte[] content) throws Exception {
        java.nio.file.Files.write(f.toPath(), content);
        System.out.println("写出 " + f.getName() + "  (" + content.length + " 字节)");
    }
}
