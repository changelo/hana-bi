package com.anur.io.store

import com.anur.core.elect.operator.ElectOperator
import com.anur.core.struct.OperationTypeEnum
import com.anur.core.struct.base.Operation
import com.anur.io.store.log.LogManager

/**
 * Created by Anur IjuoKaruKas on 2019/7/14
 */
class TestAppendThenDiscard

fun main() {

    ElectOperator.getInstance().start()

    try {
        for (i in 0..999999) {
            val operation = Operation(OperationTypeEnum.SETNX, "setAnur", "ToIjuoKaruKas")
            LogManager.append(operation)
        }
    } catch (e: Exception) {
    }



    println()

}