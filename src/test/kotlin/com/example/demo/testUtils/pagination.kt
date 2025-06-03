package com.example.demo.testUtils

fun calculateExpectedPageSize(totalItems: Int, page: Int, pageSize: Int): Int {
    val startIndex = (page - 1) * pageSize
    return when {
        startIndex >= totalItems -> 0
        startIndex + pageSize <= totalItems -> pageSize
        else -> totalItems - startIndex
    }
}