package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     *
     * @param timeoutSec
     * @return boolean
     * @author Ddalao
     * @create 2023/10/9
     **/
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     *
     * @return void
     * @author Ddalao
     * @create 2023/10/9
     **/
    void unlock();
}
