package com.hc.basiclibrary.viewBasic;

import android.app.Application;

import com.jeremyliao.liveeventbus.LiveEventBus;

/*
*
* App启动时会同时启动的类
* */
public class HomeApplication extends Application {

    //判断是否登录,已经登录：isLogin：true，未登录：isLogin：false
    private String isLogin;
    //判断权限等级，是管理员还是普通用户 limits = "ordinary" 为普通用户，limits="admin"为管理员
    private String limits;
    public void setIsLogin(String loginInfo){
        this.isLogin = loginInfo;
    }
    public String getIsLogin()
    {
        return isLogin;
    }

    public String getLimits() {
        return limits;
    }

    public void setLimits(String limits) {
        this.limits = limits;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LiveEventBus.config().autoClear(true)
                .lifecycleObserverAlwaysActive(true).enableLogger(false);
        setIsLogin("false");
        //默认为普通用户
        setLimits("Ordinary");
    }
}
