package com.anur.engine

import com.anur.core.struct.base.Operation
import com.anur.engine.api.constant.StorageTypeConst
import com.anur.engine.api.constant.TransactionTypeConst
import com.anur.engine.api.constant.common.CommonApiConst
import com.anur.engine.api.constant.str.StrApiConst
import com.anur.engine.queryer.EngineDataQueryer
import com.anur.engine.storage.core.HanabiEntry
import com.anur.engine.storage.memory.MemoryMVCCStorageUnCommittedPart
import com.anur.engine.trx.lock.TrxFreeQueuedSynchronizer
import com.anur.engine.trx.manager.TrxManager
import org.slf4j.LoggerFactory

/**
 * Created by Anur IjuoKaruKas on 2019/10/24
 *
 * 数据流转控制，具体参考文档 TrxDesign.MD
 */
object EngineDataFlowControl {

    private val logger = LoggerFactory.getLogger(EngineDataFlowControl::class.java)

    fun commandInvoke(opera: Operation) {
        val cmd = opera.hanabiCommand
        val key = opera.key
        val value = cmd.getValue()
        val trxId = cmd.getTrxId()
asdfadsfdsaf
        TrxManager.acquireTrx(trxId)

        /*
         * common 操作比较特殊，它直接会有些特殊交互，比如开启一个事务，关闭一个事务等。
         */
        when (StorageTypeConst.map(cmd.getType())) {
            StorageTypeConst.COMMON -> {
                when (cmd.getApi()) {
                    CommonApiConst.START_TRX -> {
                        return
                    }
                    CommonApiConst.COMMIT_TRX -> {
                        doCommit(trxId)
                        return
                    }
                }
            }
        }

        var isSelect = false

        when (StorageTypeConst.map(cmd.getType())) {
            StorageTypeConst.STR -> {
                when (cmd.getApi()) {
                    StrApiConst.SELECT -> {
                        logger.info("key [$key] trx [$trxId] 查询结果： ${EngineDataQueryer.doQuery(trxId, key)}")
                        isSelect = true
                    }
                    StrApiConst.INSERT -> doAcquire(trxId, key, value, StorageTypeConst.STR, HanabiEntry.Companion.OperateType.ENABLE)
                    StrApiConst.UPDATE -> doAcquire(trxId, key, value, StorageTypeConst.STR, HanabiEntry.Companion.OperateType.ENABLE)
                    StrApiConst.DELETE -> doAcquire(trxId, key, value, StorageTypeConst.STR, HanabiEntry.Companion.OperateType.DISABLE)
                }
            }
        }

        /*
         * 如果是短事务，操作完就直接提交
         */
        val isShortTrx = TransactionTypeConst.map(cmd.getTransactionType()) == TransactionTypeConst.SHORT
        if (isShortTrx && !isSelect) doCommit(trxId)
    }

    /**
     * 进行事务控制与数据流转，通过无锁控制来进行阻塞与唤醒
     *
     * 如果拿到锁，则调用api，将数据插入 未提交部分(uc)
     */
    private fun doAcquire(trxId: Long, key: String, value: String, storageType: StorageTypeConst, operateType: HanabiEntry.Companion.OperateType) {
        TrxFreeQueuedSynchronizer.acquire(trxId, key) {
            MemoryMVCCStorageUnCommittedPart.commonOperate(trxId, key,
                    HanabiEntry(storageType, value, operateType)
            )
        }
    }

    /**
     * 进行事务控制与数据流转
     */
    private fun doCommit(trxId: Long) {
        TrxFreeQueuedSynchronizer.release(trxId) { keys ->
            keys?.let { MemoryMVCCStorageUnCommittedPart.flushToCommittedPart(trxId, it) }
            TrxManager.releaseTrx(trxId)
        }
    }
}