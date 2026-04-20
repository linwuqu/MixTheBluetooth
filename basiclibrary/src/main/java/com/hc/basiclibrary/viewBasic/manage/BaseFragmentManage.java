package com.hc.basiclibrary.viewBasic.manage;

import android.util.SparseArray;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.hc.basiclibrary.R;
import com.hc.basiclibrary.viewBasic.BaseFragment;

import java.util.ArrayList;

import java.util.List;


public class BaseFragmentManage {

    private final List<FragmentMessage> mFragments = new ArrayList<>();
    private final int mViewId;
    private int mPreviousFragmentLocation = -1; //之前fragment数组的位置
    private final SparseArray<BaseFragment<?>> mArray = new SparseArray<>();
    private final List<Integer> mFragmentOrder = new ArrayList<>();
    private final FragmentActivity mActivity;

    public BaseFragmentManage(int viewId, FragmentActivity activity){
        mViewId = viewId;
        mActivity = activity;
    }

    //添加所有的fragment，按照从左到右的顺序,不然会抛出异常
    public void addFragment(int id, BaseFragment<?> fragment){
        int length = mArray.size();
        FragmentTransaction transaction = mActivity.getSupportFragmentManager().beginTransaction();
        mArray.put(id,fragment);
        if (length < mArray.size()){

            mFragments.add(new FragmentMessage(id, fragment));
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.add(mViewId,fragment);
            transaction.commit();//提交

            for (Integer integer : mFragmentOrder) {
                if (integer == id) return;
            }
            mFragmentOrder.add(id);
        }
        hideFragment(transaction);//隐藏所有的Fragment
    }

    public void showFragment(int id){
        if (id == getFragmentId())
            return;
        final FragmentTransaction transaction = mActivity.getSupportFragmentManager().beginTransaction();
        if (getFragmentId() != -1) {
            transaction.setCustomAnimations(selectAnim(id), 0);
        } else {
            selectAnim(id);
        }
        hideFragment(transaction);//隐藏所有的Fragment
        for (final FragmentMessage mFragment : mFragments) {
            if (mFragment.getViewId() == id){
                mActivity.runOnUiThread(() -> {
                    transaction.show(mFragment.getFragment());//展示指定的Fragment
                    transaction.commit();//提交
                    mFragment.setHide(false);//设置为非隐藏
                });
                return;
            }
        }
        throw new SecurityException("请先加载id为"+id+"的fragment,请调用addFragment()");
    }


    //隐藏所有
    private void hideFragment(FragmentTransaction transaction){
        for (FragmentMessage mFragment : mFragments) {
            transaction.hide(mFragment.getFragment());
            mFragment.setHide(true);//设置标志为隐藏
        }
    }

    //删除一个fragment后，要把他重新加载尽来
    public void delete(int viewId){
        FragmentTransaction transaction = mActivity.getSupportFragmentManager().beginTransaction();
        for (FragmentMessage mFragment : mFragments) {
            if (mFragment.getViewId() == viewId){
                transaction.remove(mFragment.getFragment());
                mFragments.remove(mFragment);
                transaction.commit();
                break;
            }
        }
    }

    //获取没有被隐藏的Fragment
    private int getFragmentId(){
        for (FragmentMessage mFragment : mFragments) {
            if (!mFragment.getHide()){
                return mFragment.getViewId();
            }
        }
        return -1;
    }


    private int selectAnim(int id) {
        boolean isRight = false;
        if (mPreviousFragmentLocation != -1) {
            isRight = mPreviousFragmentLocation < getArrayLocation(id);
        }
        mPreviousFragmentLocation = getArrayLocation(id);

        return isRight?R.anim.fragment_right:R.anim.fragment_left;
    }


    private int getArrayLocation(int id){
        for(int i= 0;i<mFragmentOrder.size();i++){
            if (mFragmentOrder.get(i) == id){
                return i;
            }
        }
        return -1;
    }


    private static class FragmentMessage{
        private final int mViewId;
        private final BaseFragment<?> mFragment;
        private boolean mHide = true;//初始为隐藏
        FragmentMessage(int viewId, BaseFragment<?> fragments){
            this.mFragment = fragments;
            this.mViewId = viewId;
        }

        BaseFragment<?> getFragment() {
            return mFragment;
        }

        int getViewId() {
            return mViewId;
        }

        void setHide(boolean mHide) {
            this.mHide = mHide;
        }

        boolean getHide(){
            return mHide;
        }
    }



}
