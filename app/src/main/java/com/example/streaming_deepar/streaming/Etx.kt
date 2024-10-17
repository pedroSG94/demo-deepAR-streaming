package com.example.streaming_deepar.streaming

import com.pedro.common.toByteArray
import java.nio.ByteBuffer

fun ByteBuffer.clone(): ByteBuffer = ByteBuffer.wrap(toByteArray())