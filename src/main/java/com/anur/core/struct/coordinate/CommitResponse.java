package com.anur.core.struct.coordinate;

import java.nio.ByteBuffer;
import com.anur.core.elect.model.GenerationAndOffset;
import com.anur.core.struct.OperationTypeEnum;
import com.anur.core.struct.base.AbstractTimedStruct;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

/**
 * Created by Anur IjuoKaruKas on 4/8/2019
 *
 * 当收到leader发来的可提交进度时,进行进度提交,并且进行当前最大提交进度的回包
 */
public class CommitResponse extends AbstractTimedStruct {

    public static final int CommitGenerationOffset = TimestampOffset + TimestampLength;

    public static final int CommitGenerationLength = 8;

    public static final int CommitOffsetOffset = CommitGenerationOffset + CommitGenerationLength;

    public static final int CommitOffsetLength = 8;

    public static final int BaseMessageOverhead = CommitOffsetOffset + CommitOffsetLength;

    public CommitResponse(GenerationAndOffset CommitGAO) {
        init(BaseMessageOverhead, OperationTypeEnum.COMMIT_RESPONSE, buffer -> {
            buffer.putLong(CommitGAO.getGeneration());
            buffer.putLong(CommitGAO.getOffset());
        });
    }

    public CommitResponse(ByteBuffer byteBuffer) {
        this.buffer = byteBuffer;
    }

    public GenerationAndOffset getCommitGAO() {
        return new GenerationAndOffset(buffer.getLong(CommitGenerationOffset), buffer.getLong(CommitOffsetOffset));
    }

    @Override
    public void writeIntoChannel(Channel channel) {
        channel.write(Unpooled.wrappedBuffer(buffer));
    }

    @Override
    public int totalSize() {
        return size();
    }

    @Override
    public String toString() {
        return "CommitResponse{ GAO => " + getCommitGAO() + " }";
    }
}
