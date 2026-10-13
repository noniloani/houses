package com.meta.wearable.dat.externalsampleapps.displayaccess.scan

/** Owns callbacks per scan so delayed Android events cannot stop a later run. */
class ScanLease {
  private var sequence = 0L
  var token: Long? = null
    private set
  private var callback: (() -> Unit)? = null
  fun begin(onStop: () -> Unit): Long {
    if (token == null) token = ++sequence
    callback = onStop
    return token!!
  }
  fun revoke(): Long? {
    val old = token
    token = null; callback = null
    return old
  }
  fun finish(owner: Long) {
    if (token != owner) return
    val action = callback
    token = null; callback = null
    action?.invoke()
  }
}
