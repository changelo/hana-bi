/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anur.core.log.operation;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import com.anur.core.log.common.OffsetAndPosition;
import com.anur.core.log.common.OperationAndOffset;
import com.anur.core.log.common.OperationConstant;
import com.anur.core.util.FileIOUtil;
import com.anur.core.util.IteratorTemplate;
import com.anur.exception.HanabiException;

/**
 * Created by Anur IjuoKaruKas on 2/25/2019
 *
 * 高仿kafka FileMessageSet 写的，是操作日志在磁盘的映射类。
 */
public class FileOperationSet extends OperationSet {

    /**
     * 对应着磁盘上的一个文件
     */
    private volatile File file;

    /**
     * 用于读写日志，是此文件的 channel
     */
    private FileChannel fileChannel;

    /**
     * OperationSet 开始的绝对位置的下界
     */
    private int start;

    /**
     * OperationSet 结束的绝对位置的上限
     */
    private int end;

    /**
     * 表示这个类是否表示文件的一部分，也就是切片
     */
    private boolean isSlice;

    /**
     * 本FileOperationSet的大小
     */
    private AtomicInteger _size;

    /**
     * 创建一个非分片的FileOperationSet
     */
    public FileOperationSet(File file) throws IOException {
        this.file = file;
        this.fileChannel = FileIOUtil.openChannel(file, true);
        this.start = 0;
        this.end = Integer.MAX_VALUE;
        this.isSlice = false;
        this._size = new AtomicInteger(Math.min((int) fileChannel.size(), end));
    }

    /**
     * 创建一个非分片的FileOperationSet
     */
    public FileOperationSet(File file, boolean mutable) throws IOException {
        this.file = file;
        this.fileChannel = FileIOUtil.openChannel(file, mutable);
        this.start = 0;
        this.end = Integer.MAX_VALUE;
        this.isSlice = false;
        this._size = new AtomicInteger(Math.min((int) fileChannel.size(), end));
    }

    /**
     * 创建一个非分片的FileOperationSet
     */
    public FileOperationSet(File file, FileChannel fileChannel) throws IOException {
        this.file = file;
        this.fileChannel = fileChannel;
        this.start = 0;
        this.end = Integer.MAX_VALUE;
        this.isSlice = false;
        this._size = new AtomicInteger(Math.min((int) fileChannel.size(), end));
    }

    /**
     * 创建一个文件分片
     */
    public FileOperationSet(File file, FileChannel fileChannel, int start, int end) throws IOException {
        this.file = file;
        this.fileChannel = fileChannel;
        this.start = start;
        this.end = end;
        this.isSlice = true;
        this._size = new AtomicInteger(end - start);// 如果只是一个切片，我们不用去检查它的大小
    }

    /**
     * 将操作记录添加到文件中
     */
    public void append(ByteBufferOperationSet byteBufferOperationSet) throws IOException {
        int written = byteBufferOperationSet.writeFullyTo(this.fileChannel);
        this._size.getAndAdd(written);
    }

    /**
     * 从startingPosition开始，找到第一个大于等于目标offset的物理地址
     *
     * 如果找不到，则返回null
     */
    public OffsetAndPosition searchFor(long targetOffset, int startingPosition) throws IOException {
        int position = startingPosition;
        ByteBuffer buffer = ByteBuffer.allocate(OperationSet.LogOverhead);
        int size = this.sizeInBytes();

        // 没有越界之前，可以无限搜索
        while (position + OperationSet.LogOverhead < size) {
            buffer.rewind(); // 重置一下buffer指针
            fileChannel.read(buffer, position); // 读取文件的offset值

            if (buffer.hasRemaining()) {
                throw new IllegalStateException(String.format("Failed to read complete buffer for targetOffset %d startPosition %d in %s", targetOffset, startingPosition, file.getAbsolutePath()));
            }
            buffer.rewind(); // 重置一下buffer指针

            long offset = buffer.getLong();
            if (offset >= targetOffset) {
                return new OffsetAndPosition(offset, position);
            }

            int messageSize = buffer.getInt(); // 8字节的offset后面紧跟着4字节的这条消息的长度

            if (messageSize < OperationConstant.MinMessageOverhead) {
                throw new IllegalStateException("Invalid message size: " + messageSize);
            }

            position += OperationSet.LogOverhead + messageSize;
        }
        return null;
    }

    /**
     * 将某个日志文件进行裁剪到指定大小，必须小于文件的size
     */
    public int druncateTo(int targetSize) throws IOException {
        int originalSize = this.sizeInBytes();
        if (targetSize > originalSize || targetSize < 0) {
            throw new HanabiException("尝试将日志文件截短成 " + targetSize + " bytes 但是失败了, " +
                " 原文件大小为 " + originalSize + " bytes。");
        }

        if (targetSize < fileChannel.size()) {
            fileChannel.truncate(targetSize);
            fileChannel.position(targetSize);
            _size.set(targetSize);
        }
        return originalSize - targetSize;
    }

    /**
     * The number of bytes taken up by this file set
     */
    public int sizeInBytes() {
        return _size.get();
    }

    /**
     * Write some of this set to the given channel.
     *
     * 将FileMessageSet的部分数据写到指定的channel上
     *
     * @param destChannel The channel to write to.
     * @param writePosition The position in the message set to begin writing from.
     * @param size The maximum number of bytes to write
     *
     * @return The number of bytes actually written.
     */
    @Override
    public int writeTo(GatheringByteChannel destChannel, long writePosition, int size) throws IOException {
        // 进行边界检查
        int newSize = Math.min((int) fileChannel.size(), end) - start;
        if (newSize < _size.get()) {
            throw new HanabiException(String.format("FileOperationSet 的文件大小 %s 在写的过程中被截断了：之前的文件大小为 %d, 现在的文件大小为 %d", file.getAbsolutePath(), _size.get(), newSize));
        }
        int position = start + (int) writePosition; // The position in the message set to begin writing from.
        int count = Math.min(size, sizeInBytes());

        // 将从position开始的count个bytes写到指定到channel中
        int bytesTransferred = (int) fileChannel.transferTo(position, count, destChannel);
        return bytesTransferred;
    }

    /**
     * 获取各种消息的迭代器
     */
    @Override
    public Iterator<OperationAndOffset> iterator() {
        return this.iterator(Integer.MAX_VALUE);
    }

    /**
     * 获取某个文件的迭代器
     */
    public Iterator<OperationAndOffset> iterator(int maxMessageSize) {
        return new IteratorTemplate<OperationAndOffset>() {

            private int location = start;

            private int sizeOffsetLength = OperationSet.LogOverhead;

            private ByteBuffer sizeOffsetBuffer = ByteBuffer.allocate(sizeOffsetLength);

            @Override
            protected OperationAndOffset makeNext() {
                if (location + sizeOffsetLength >= end) {// 如果已经到了末尾，返回空
                    return allDone();
                }

                // read the size of the item
                sizeOffsetBuffer.rewind();
                try {
                    fileChannel.read(sizeOffsetBuffer, location);
                } catch (IOException e) {
                    throw new HanabiException("Error occurred while reading data from fileChannel.");
                }
                if (sizeOffsetBuffer.hasRemaining()) {// 这也是读到了末尾
                    return allDone();
                }

                long offset = sizeOffsetBuffer.getLong();
                int size = sizeOffsetBuffer.getInt();

                if (size < OperationConstant.MinMessageOverhead || location + sizeOffsetLength + size > end) { // 代表消息放不下了
                    return allDone();
                }

                if (size > maxMessageSize) {
                    throw new HanabiException(String.format("Message size exceeds the largest allowable message size (%d).", maxMessageSize));
                }

                // read the item itself
                ByteBuffer buffer = ByteBuffer.allocate(size);
                try {
                    fileChannel.read(buffer, location + sizeOffsetLength);
                } catch (IOException e) {
                    throw new HanabiException("Error occurred while reading data from fileChannel.");
                }
                if (buffer.hasRemaining()) {// 代表没读完，其实和上面一样，可能是消息写到最后写不下了，或者被截取截没了
                    return allDone();
                }
                buffer.rewind();

                // increment the location and return the item
                location += size + sizeOffsetLength;
                return new OperationAndOffset(new Operation(buffer), offset);
            }
        };
    }
}