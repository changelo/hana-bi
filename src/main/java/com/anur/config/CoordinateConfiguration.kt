package com.anur.config

import com.anur.config.common.ConfigHelper
import com.anur.config.common.ConfigurationEnum

/**
 * Created by Anur IjuoKaruKas on 2019/7/5
 */
object CoordinateConfiguration : ConfigHelper() {

    fun getFetchBackOfMs(): Int {
        return getConfig(ConfigurationEnum.COORDINATE_FETCH_BACK_OFF_MS) { Integer.valueOf(it) } as Int
    }
}
