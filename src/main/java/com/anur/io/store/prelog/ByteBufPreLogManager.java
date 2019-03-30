package com.anur.io.store.prelog;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.anur.core.elect.model.GenerationAndOffset;
import com.anur.core.lock.ReentrantReadWriteLocker;
import com.anur.exception.HanabiException;
import com.anur.io.store.common.OperationAndOffset;
import com.anur.io.store.log.LogManager;
import com.anur.io.store.operationset.ByteBufferOperationSet;
import io.netty.buffer.ByteBuf;

/**
 * Created by Anur IjuoKaruKas on 2019/3/23
 */
public class ByteBufPreLogManager extends ReentrantReadWriteLocker {

    private volatile static ByteBufPreLogManager INSTANCE;

    public static ByteBufPreLogManager getINSTANCE() {
        if (INSTANCE == null) {
            synchronized (ByteBufPreLogManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ByteBufPreLogManager();
                }
            }
        }
        return INSTANCE;
    }

    private final ConcurrentSkipListMap<Long, ByteBufPreLog> preLog;

    private Logger logger = LoggerFactory.getLogger(ByteBufPreLogManager.class);

    /**
     * 当前已经提交的 offset
     */
    private GenerationAndOffset commitOffset;

    /**
     * 预日志最后一个 offset
     */
    private GenerationAndOffset preLogOffset;

    public ByteBufPreLogManager() {
        this.preLog = new ConcurrentSkipListMap<>();
        this.commitOffset = LogManager.getINSTANCE()
                                      .getInitial();
        this.preLogOffset = commitOffset;
    }

    /**
     * 此添加必须保证所有的操作日志都在同一个世代，实际上也确实如此
     */
    public void append(long generation, ByteBufferOperationSet byteBufferOperationSet) {
        this.writeLockSupplier(() -> {
            ByteBufPreLog byteBufPreLog = preLog.compute(generation, (aLong, b) -> b == null ? new ByteBufPreLog(generation) : b);
            Iterator<OperationAndOffset> iterator = byteBufferOperationSet.iterator();

            long lastOffset = -1;
            while (iterator.hasNext()) {
                OperationAndOffset operationAndOffset = iterator.next();
                byteBufPreLog.append(operationAndOffset.getOperation(), operationAndOffset.getOffset());
                lastOffset = operationAndOffset.getOffset();
            }

            if (lastOffset != -1) {
                GenerationAndOffset before = preLogOffset;
                preLogOffset = new GenerationAndOffset(generation, lastOffset);
                logger.debug("本地预日志由 {} 更新至 {}", before.toString(), preLogOffset.toString());
            }
            return null;
        });
    }

    /**
     * 将此 offset 往后的数据都提交到本地
     */
    public void commit(GenerationAndOffset GAO) {

        // 先与本地已经提交的记录做对比，只有大于本地副本提交进度时才进行commit
        int compareResult = GAO.compareTo(commitOffset);

        // 需要提交的进度小于等于preLogOffset
        if (compareResult <= 0) {
            return;
        } else {
            logger.debug("收到来自 Leader 节点的有效 Commit 请求 => {}", GAO.toString());

            GenerationAndOffset canCommit = GAO.compareTo(preLogOffset) > 0 ? preLogOffset : GAO;

            if (canCommit.equals(commitOffset)) {
                logger.debug("本地预日志为 {} ，所以可提交到 {} ，本地已经提交此进度。", preLogOffset.toString(), canCommit.toString());
            } else {
                logger.debug("本地预日志为 {} ，所以可提交到 {}", preLogOffset.toString(), canCommit.toString());

                ByteBuf byteBuf = getBefore(canCommit);
            }
        }
    }

    /**
     * 获取当前这一条之前的数据（包括这一条）
     */
    private ByteBuf getBefore(GenerationAndOffset GAO) {
        return this.readLockSupplier(() -> {
            long gen = GAO.getGeneration();
            long offset = GAO.getOffset();
            ConcurrentNavigableMap<Long, ByteBufPreLog> head = preLog.headMap(gen, true);

            if (head == null || head.size() == 0) {
                throw new HanabiException("获取预日志时：世代过小或者此世代还未有预日志");
            }

            ByteBufPreLog byteBufPreLog = head.firstEntry()
                                              .getValue();

            return byteBufPreLog.getBefore(offset);
        });
    }

    /**
     * 丢弃掉一些消息（批量丢弃，但是不会丢弃掉当前这一条）
     */
    private void discardBefore(GenerationAndOffset GAO) {
        this.writeLockSupplier(() -> {
            long gen = GAO.getGeneration();
            long offset = GAO.getOffset();
            ConcurrentNavigableMap<Long, ByteBufPreLog> head = preLog.headMap(gen, true);

            if (head == null || head.size() == 0) {
                throw new HanabiException("获取预日志时：世代过小或者此世代还未有预日志");
            }

            ByteBufPreLog byteBufPreLog = head.firstEntry()
                                              .getValue();
            byteBufPreLog.discardBefore(offset);
            return null;
        });
    }
}
