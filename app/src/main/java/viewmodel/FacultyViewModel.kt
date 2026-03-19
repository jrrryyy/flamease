package com.example.flamease.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.flamease.model.User
import com.example.flamease.repository.AuthRepository
import com.example.flamease.repository.UserRepository

class FacultyViewModel : ViewModel() {

    private val authRepo = AuthRepository()
    private val userRepo = UserRepository()

    private val _user = MutableLiveData<User>()
    val user: LiveData<User> = _user

    fun loadUser() {
        val uid = authRepo.getCurrentUserId()
        if (uid != null) {
            userRepo.getUser(uid) {
                _user.postValue(it)
            }
        }
    }
}