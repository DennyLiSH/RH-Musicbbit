package com.rabbithole.musicbbit.domain.validation

import java.io.File

/**
 * Pure domain validation for scan directory paths.
 */
object ScanDirectoryValidator {

    sealed class Error {
        /** The path does not exist or is not a directory. */
        data object InvalidPath : Error()

        /** The directory is already in the watch list. */
        data object AlreadyExists : Error()
    }

    /**
     * Validates that [path] is an existing directory and not already present in [existingPaths].
     */
    fun validate(path: String, existingPaths: List<String>): ValidationResult<Unit, Error> {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            return ValidationResult.Failure(Error.InvalidPath)
        }
        if (existingPaths.contains(path)) {
            return ValidationResult.Failure(Error.AlreadyExists)
        }
        return ValidationResult.Success(Unit)
    }

    sealed class ValidationResult<out T, out E> {
        data class Success<T>(val value: T) : ValidationResult<T, Nothing>()
        data class Failure<E>(val error: E) : ValidationResult<Nothing, E>()
    }
}
