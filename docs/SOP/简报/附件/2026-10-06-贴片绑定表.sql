-- 贴片绑定方案 V2 的 SQLite 参考结构。
-- 尚未加入 migratedev；施工时转为 SQLDelight .sq 与增量迁移。
PRAGMA foreign_keys = ON;

CREATE TABLE sensorPatch (
    patchId TEXT NOT NULL PRIMARY KEY,
    schemaVersion INTEGER NOT NULL CHECK (schemaVersion = 1),
    modelId INTEGER NOT NULL CHECK (modelId BETWEEN 0 AND 65535),
    lotNo TEXT NOT NULL,
    manufacturedOn TEXT NOT NULL,
    expiresOn TEXT NOT NULL CHECK (expiresOn >= manufacturedOn),
    maxUseMinutes INTEGER NOT NULL CHECK (maxUseMinutes BETWEEN 1 AND 65535),
    calibrationRef TEXT NOT NULL,
    codeBytes BLOB NOT NULL,
    keyId INTEGER NOT NULL CHECK (keyId BETWEEN 0 AND 65535),
    verifiedAtMillis INTEGER NOT NULL,
    infoSnapshotJson TEXT NOT NULL
);

CREATE TABLE patchUse (
    useId TEXT NOT NULL PRIMARY KEY,
    patchId TEXT NOT NULL UNIQUE REFERENCES sensorPatch(patchId) ON DELETE RESTRICT,
    userId TEXT NOT NULL,
    deviceId TEXT NOT NULL,
    activatedAtMillis INTEGER NOT NULL,
    endedAtMillis INTEGER,
    expiresAtMillis INTEGER NOT NULL CHECK (expiresAtMillis > activatedAtMillis),
    state TEXT NOT NULL CHECK (state IN ('ACTIVE', 'ENDED', 'EXPIRED')),
    baselineJson TEXT NOT NULL,
    patchSnapshotJson TEXT NOT NULL,
    CHECK (
        (state = 'ACTIVE' AND endedAtMillis IS NULL) OR
        (state IN ('ENDED', 'EXPIRED') AND endedAtMillis IS NOT NULL
            AND endedAtMillis >= activatedAtMillis)
    )
);

CREATE UNIQUE INDEX oneActivePatchPerDevice
ON patchUse(deviceId) WHERE state = 'ACTIVE';

CREATE INDEX patchUsesByUser ON patchUse(userId, activatedAtMillis);

CREATE TRIGGER patchUseIdentityImmutable
BEFORE UPDATE OF patchId, userId, deviceId, activatedAtMillis ON patchUse
WHEN NEW.patchId != OLD.patchId OR NEW.userId != OLD.userId OR
     NEW.deviceId != OLD.deviceId OR NEW.activatedAtMillis != OLD.activatedAtMillis
BEGIN
    SELECT RAISE(ABORT, 'patch use identity is immutable');
END;

CREATE TABLE measurementRound (
    roundId TEXT NOT NULL PRIMARY KEY,
    sourceKey TEXT NOT NULL UNIQUE,
    deviceId TEXT NOT NULL,
    firstEisLog TEXT NOT NULL,
    startedAtDeviceText TEXT NOT NULL,
    rawHash TEXT,
    rawFilePath TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PARTIAL', 'COMPLETE', 'CONFLICT')),
    useId TEXT REFERENCES patchUse(useId) ON DELETE RESTRICT,
    bindingStatus TEXT NOT NULL CHECK (
        bindingStatus IN ('UNBOUND', 'PENDING', 'CONFIRMED')
    ),
    bindingMethod TEXT CHECK (
        bindingMethod IS NULL OR
        bindingMethod IN ('USER_CONFIRMED', 'FIRMWARE_REF')
    ),
    validity TEXT NOT NULL CHECK (validity IN ('UNKNOWN', 'VALID', 'EXPIRED')),
    metadataSnapshotJson TEXT,
    archiveState TEXT NOT NULL CHECK (archiveState IN ('EXPORT_PENDING', 'READY')),
    updatedAtMillis INTEGER NOT NULL,
    userId TEXT NOT NULL,
    CHECK (
        (bindingStatus = 'CONFIRMED' AND useId IS NOT NULL
            AND bindingMethod IS NOT NULL AND metadataSnapshotJson IS NOT NULL) OR
        (bindingStatus IN ('UNBOUND', 'PENDING') AND useId IS NULL
            AND bindingMethod IS NULL)
    ),
    CHECK (state != 'COMPLETE' OR rawHash IS NOT NULL)
);

CREATE INDEX roundsByPatchUse ON measurementRound(useId);
CREATE INDEX roundsByDevice ON measurementRound(deviceId, startedAtDeviceText);
CREATE INDEX roundsByUser ON measurementRound(userId, startedAtDeviceText);

-- 数据库校验来源设备。业务事务还要核对归属账号及测量边界。
CREATE TRIGGER roundUseDeviceMatchInsert
BEFORE INSERT ON measurementRound
WHEN NEW.useId IS NOT NULL
BEGIN
    SELECT CASE WHEN NOT EXISTS (
        SELECT 1 FROM patchUse
        WHERE useId = NEW.useId AND deviceId = NEW.deviceId AND userId = NEW.userId
    ) THEN RAISE(ABORT, 'patch use device mismatch') END;
END;

CREATE TRIGGER roundUseDeviceMatchUpdate
BEFORE UPDATE OF useId, deviceId, userId ON measurementRound
WHEN NEW.useId IS NOT NULL
BEGIN
    SELECT CASE WHEN NOT EXISTS (
        SELECT 1 FROM patchUse
        WHERE useId = NEW.useId AND deviceId = NEW.deviceId AND userId = NEW.userId
    ) THEN RAISE(ABORT, 'patch use device mismatch') END;
END;

CREATE TABLE syncOutbox (
    eventId TEXT NOT NULL PRIMARY KEY,
    objectType TEXT NOT NULL CHECK (objectType IN ('PATCH_USE', 'MEASUREMENT_ROUND')),
    objectId TEXT NOT NULL,
    eventType TEXT NOT NULL,
    payloadJson TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'SYNCED', 'CONFLICT')),
    retryCount INTEGER NOT NULL DEFAULT 0 CHECK (retryCount >= 0),
    createdAtMillis INTEGER NOT NULL
);
CREATE INDEX pendingSyncEvents ON syncOutbox(state, createdAtMillis);
