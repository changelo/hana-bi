package com.anur.engine.trx.manager

import com.anur.core.lock.rentrant.ReentrantReadWriteLocker
import com.anur.engine.trx.lock.TrxFreeQueuedSynchronizer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Supplier


/**
 * Created by Anur IjuoKaruKas on 2019/10/15
 *
 * 事务管理器
 */
object TrxManager {

    private const val interval = 128L

    private val logger: Logger = LoggerFactory.getLogger(TrxFreeQueuedSynchronizer::class.java)

    private val locker = ReentrantReadWriteLocker()

    private val waterHolder = mutableMapOf<Long, TrxSegment>()

    private var nowTrx: Long = Long.MIN_VALUE

    fun allocate(): Long {
        return locker.writeLockSupplierCompel(Supplier {
            nowTrx += 1
            intoWaterHolder(nowTrx)
            return@Supplier nowTrx
        })
    }

    fun commitFromWaterHolder(anyElse: Long) {
        locker.writeLocker() {
            val index = anyElse / interval
            val trxSegment = waterHolder[index]

            when (trxSegment) {
                null -> logger.error("重复提交？？？？？？？？？？？？？？？？？？？？？")
                else -> {
                    trxSegment.release(anyElse)
                }
            }
        }
    }

    fun intoWaterHolder(anyElse: Long) {
        locker.writeLocker {
            val index = anyElse / interval
            if (!waterHolder.contains(index)) waterHolder[index] = TrxSegment(anyElse)
            waterHolder[index]!!.acquire(anyElse)
        }
    }


    /**
     * 为了避免事务太多，列表太大，故采用分段
     */
    class TrxSegment(anyElse: Long) {

        private var trxBitMap = 0

        private var minTrxId = anyElse

        val start: Long = anyElse / interval

        fun acquire(trxId: Long) {
            val index = ((trxId - 1) and interval).toInt()
            val mask = 1.shl(index)
            trxBitMap = trxBitMap and mask

            if (trxId < minTrxId) {
                minTrxId = trxId
            }
        }

        fun release(trxId: Long) {
            val index = ((trxId - 1) and interval).toInt()
            val mask = 1.shl(index)
            trxBitMap = mask.inv() and trxBitMap
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TrxSegment

            if (start != other.start) return false

            return true
        }

        override fun hashCode(): Int {
            return start.hashCode()
        }
    }
}

fun main() {
    val seg = TrxManager.TrxSegment(11)
    seg.acquire(12)
    seg.acquire(13)
    seg.release(12)

    println()
}