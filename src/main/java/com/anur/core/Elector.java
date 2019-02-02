package com.anur.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Anur IjuoKaruKas on 2019/1/21
 *
 * 进行leader选举的核心控制类
 */
public class Elector {

    static Logger logger = LoggerFactory.getLogger(Elector.class);

    public static void main(String[] args) {
        ElectServerOperator.getInstance()
                           .start();
        VoteOperator.getInstance()
                    .beginElect();
    }
}
