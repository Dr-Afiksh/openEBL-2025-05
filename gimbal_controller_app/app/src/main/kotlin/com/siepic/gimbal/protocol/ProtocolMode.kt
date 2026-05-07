package com.siepic.gimbal.protocol

/**
 * Which packet the joystick mode emits on each tick.
 *
 * - LEGACY_FLOAT_A: send !A<x,y,z> floats, reusing the gimbal's accelerometer
 *   handler. Works with current firmware unchanged.
 * - SIMPLE_J: send !J<x_int8><y_int8>. Requires a firmware patch — current
 *   sketch does not understand !J. Kept here for forward-compat experiments.
 */
enum class ProtocolMode { LEGACY_FLOAT_A, SIMPLE_J }
