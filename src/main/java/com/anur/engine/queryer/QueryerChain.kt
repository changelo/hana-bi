package com.anur.engine.queryer

import com.anur.engine.storage.core.HanabiEntry


/**
 * Created by Anur IjuoKaruKas on 2019/11/27
 *
 * 由于查询功能是由各个小模块提供的，所以使用责任链来实现
 */
abstract class QueryerChain {
    var next: QueryerChain? = null
    abstract fun doQuery(trxId: Long, key: String): HanabiEntry?
    fun query(trxId: Long, key: String): HanabiEntry? = doQuery(trxId, key) ?: next?.query(trxId, key)
}