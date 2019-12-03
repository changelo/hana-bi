package com.anur.engine.storage.memory

import com.anur.core.log.Debugger
import com.anur.core.log.DebuggerLevel
import com.anur.engine.result.common.ParameterHandler
import com.anur.engine.storage.core.HanabiEntry
import com.anur.engine.storage.core.VerAndHanabiEntryWithKeyPair
import com.anur.engine.storage.core.VerAndHanabiEntry
import com.anur.exception.MemoryMVCCStorageUnCommittedPartException
import java.util.*

/**
 * Created by Anur IjuoKaruKas on 2019/10/11
 *
 * 内存存储实现，支持 mvcc （未提交部分）
 */
object MemoryMVCCStorageUnCommittedPartExecutor {

    private val logger = Debugger(MemoryMVCCStorageUnCommittedPartExecutor.javaClass)

    private val treeMap = TreeMap<String, VerAndHanabiEntry>()

    /**
     * 查找未提交的 hanabiEntry，传入的 trxId 必须为此 key 持有的那个事务id才可以查到，否则
     * 由于隔离性，未提交的不可以查出来
     */
    fun queryKeyInTrx(trxId: Long, key: String): HanabiEntry? {
        val verAndHanabiEntry = treeMap[key]
        if (verAndHanabiEntry != null && verAndHanabiEntry.trxId == trxId) {
            return verAndHanabiEntry.hanabiEntry
        }
        return null
    }

    /**
     * 将数据存入 unCommit 部分
     */
    fun commonOperate(parameterHandler: ParameterHandler) {
        val key = parameterHandler.key
        val trxId = parameterHandler.trxId
        val hanabiEntry = parameterHandler.genHanabiEntry()
        if (treeMap.containsKey(key) && treeMap[key]!!.trxId != trxId) {
            throw MemoryMVCCStorageUnCommittedPartException("mvcc uc部分出现了奇怪的bug，讲道理一个 key 只会对应一个 val，注意无锁控制 TrxFreeQueuedSynchronizer 是否有问题！")
        } else {
            logger.debug("key [$key] val [${hanabiEntry.value}] 已经进入待提交区域")
            treeMap[key] = VerAndHanabiEntry(trxId, hanabiEntry)
        }
    }

    /**
     * 将数据推入 commit 部分
     */
    fun flushToCommittedPart(trxId: Long, holdKeys: MutableSet<String>) {
        val verAndHanabiEntryWithKeyPairList = holdKeys.map {
            VerAndHanabiEntryWithKeyPair(it,
                    treeMap[it]
                            ?: throw MemoryMVCCStorageUnCommittedPartException("mvcc uc部分出现了奇怪的bug，讲道理holdKeys拥有所有key的值，注意无锁控制是否有问题！"))
        }
        logger.debug("事务 $trxId 以及其键们 [${holdKeys}] 已经进入待提交区域")
        MemoryMVCCStorageCommittedPartExecutor.commonOperate(trxId, verAndHanabiEntryWithKeyPairList)

        // 必须要先拿出来，存到 commit 的才可以删除，不然查询的时候可能会有疏漏
        holdKeys.forEach { treeMap.remove(it) }
    }
}
