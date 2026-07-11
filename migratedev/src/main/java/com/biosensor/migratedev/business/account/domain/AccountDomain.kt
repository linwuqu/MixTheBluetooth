package com.biosensor.migratedev.business.account.domain

data class AccountValidation(
    val valid: Boolean,
    val message: String? = null
)

interface AccountRules {
    fun validatePhone(phone: String): AccountValidation
    fun validatePassword(password: String): AccountValidation
    fun validateUsername(username: String): AccountValidation
}
