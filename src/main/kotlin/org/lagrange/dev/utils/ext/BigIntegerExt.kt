package org.lagrange.dev.utils.ext

import java.math.BigInteger

internal fun BigInteger.isEven(): Boolean = this.and(BigInteger.ONE) == BigInteger.ZERO