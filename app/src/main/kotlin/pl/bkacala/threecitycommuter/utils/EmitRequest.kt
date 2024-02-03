package pl.bkacala.threecitycommuter.utils

import kotlin.random.Random

class EmitRequest {

    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return Random.nextInt()
    }
}