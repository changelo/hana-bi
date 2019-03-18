package com.anur.io.store;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListMap;
import com.anur.config.InetSocketAddressConfigHelper;
import com.anur.core.elect.ElectOperator;
import com.anur.core.elect.model.GennerationAndOffset;
import com.anur.exception.HanabiException;
import com.anur.io.store.common.LogCommon;
import com.anur.io.store.common.Operation;
import com.anur.io.store.log.Log;
/**
 * Created by Anur IjuoKaruKas on 2019/3/18
 *
 * 预日志管理
 */
public class PreLogManager {

    // TODO 仅测试时，一台机器启动好几个副本，避免日志冲突
    private final String LogDir = InetSocketAddressConfigHelper.getServerName() + "\\store\\aof\\prelog\\";

    public static volatile PreLogManager INSTANCE;

    /** 管理所有 Log */
    private final ConcurrentSkipListMap<Long, Log> generationDirs;

    /** 基础目录 */
    private final File baseDir;

    /** 初始化时，最新的 Generation 和 Offset */
    private final GennerationAndOffset initial;

    public static PreLogManager getINSTANCE() {
        if (INSTANCE == null) {
            synchronized (PreLogManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PreLogManager();
                }
            }
        }
        return INSTANCE;
    }

    private PreLogManager() {
        this.generationDirs = new ConcurrentSkipListMap<>();
        String relativelyPath = System.getProperty("user.dir");
        this.baseDir = new File(relativelyPath + LogDir);
        initial = load();
    }

    /**
     * 加载既存的目录们
     */
    private GennerationAndOffset load() {
        baseDir.mkdirs();

        long latestGeneration = 0L;

        for (File file : baseDir.listFiles()) {
            if (!file.isFile()) {
                latestGeneration = Math.max(latestGeneration, Integer.valueOf(file.getName()));
            }
        }

        GennerationAndOffset init;

        // 只要创建最新的那个 generation 即可
        try {
            Log latest = new Log(latestGeneration, createGenDirIfNEX(latestGeneration), 0);
            generationDirs.put(1L, latest);
            init = new GennerationAndOffset(latestGeneration, latest.getCurrentOffset());
        } catch (IOException e) {
            throw new HanabiException("操作日志初始化失败，项目无法启动");
        }

        return init;
    }

    /**
     * 添加一条操作日志到磁盘的入口
     */
    public void append(Operation operation) {
        GennerationAndOffset operationId = ElectOperator.getInstance()
                                                        .genOperationId();

        Log log = maybeRoll(operationId.getGeneration());
        log.append(operation, operationId.getOffset());
    }

    /**
     * 在 append 操作时，如果世代更新了，则创建新的 Log 管理
     */
    private Log maybeRoll(long generation) {
        Log current = activeLog();
        if (generation > current.generation) {
            File dir = createGenDirIfNEX(generation);
            Log log;
            try {
                log = new Log(generation, dir, 0);
            } catch (IOException e) {
                throw new HanabiException("创建世代为 " + generation + " 的操作日志管理文件 Log 失败");
            }

            generationDirs.put(generation, log);
            return log;
        } else if (generation < current.generation) {
            throw new HanabiException("不应在添加日志时获取旧世代的 Log");
        }

        return current;
    }

    /**
     * 创建世代目录
     */
    private File createGenDirIfNEX(long generation) {
        return LogCommon.dirName(baseDir, generation);
    }

    /**
     * 获取最新的一个日志分片管理类 Log
     */
    public Log activeLog() {
        return generationDirs.lastEntry()
                             .getValue();
    }

    public GennerationAndOffset getInitial() {
        return initial;
    }
}
