package com.tencent.kuikly.core.pager

/** 仅构造当前 Module 注入的平台数据；真实属性由 SDK 消费编译确认。 */
class PageData(val isAndroid: Boolean = false, val isIOS: Boolean = false)
