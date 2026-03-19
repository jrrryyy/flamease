package com.example.flamease.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.flamease.repository.AuthRepository

class LoginViewModel : ViewModel() {

    private val repo = AuthRepository()

    private val _loginResult = MutableLiveData<Boolean>()
    val loginResult: LiveData<Boolean> = _loginResult

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun login(email: String, password: String) {
        repo.login(email, password) { success, message ->
            if (success) _loginResult.postValue(true)
            else _error.postValue(message)
        }
    }
}