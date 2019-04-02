package com.anur.core.struct.base;

import java.nio.ByteBuffer;
import com.anur.core.struct.OperationTypeEnum;

/**
 * Created by Anur IjuoKaruKas on 2019/3/27
 *
 * 用于向协调 Leader 注册自己
 */
public class Register extends Operation {

    public Register(String serverName) {
        super(OperationTypeEnum.REGISTER, serverName, "");
    }

    public Register(ByteBuffer buffer) {
        super(buffer);
    }

    public String getServerName() {
        return this.getKey();
    }
}
