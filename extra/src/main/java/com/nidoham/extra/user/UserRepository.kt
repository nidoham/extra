package com.nidoham.extra.user

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository for User CRUD operations
 * Handles all Firestore interactions for User model
 */
class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usersCollection = firestore.collection("users")

    // ==================== CREATE ====================

    /**
     * Create new user
     */
    suspend fun createUser(user: User): Result<User> {
        return try {
            usersCollection.document(user.userId)
                .set(user)
                .await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create user with auto-generated ID
     */
    suspend fun createUserWithAutoId(user: User): Result<User> {
        return try {
            val docRef = usersCollection.document()
            val newUser = user.copy(userId = docRef.id)
            docRef.set(newUser).await()
            Result.success(newUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== READ ====================

    /**
     * Get user by ID
     */
    suspend fun getUser(userId: String): Result<User?> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            val user = snapshot.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by username
     */
    suspend fun getUserByUsername(username: String): Result<User?> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()

            val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by phone
     */
    suspend fun getUserByPhone(phone: String): Result<User?> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("phone", phone)
                .limit(1)
                .get()
                .await()

            val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get multiple users by IDs
     */
    suspend fun getUsers(userIds: List<String>): Result<List<User>> {
        return try {
            if (userIds.isEmpty()) return Result.success(emptyList())

            // Firestore 'in' query limited to 10 items
            val users = mutableListOf<User>()
            userIds.chunked(10).forEach { chunk ->
                val snapshot = usersCollection
                    .whereIn("userId", chunk)
                    .get()
                    .await()

                snapshot.documents.mapNotNullTo(users) {
                    it.toObject(User::class.java)
                }
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search users by name or username
     */
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<User>> {
        return try {
            val lowerQuery = query.lowercase()

            // Search by username
            val usernameSnapshot = usersCollection
                .whereGreaterThanOrEqualTo("username", lowerQuery)
                .whereLessThanOrEqualTo("username", lowerQuery + '\uf8ff')
                .limit(limit.toLong())
                .get()
                .await()

            val users = usernameSnapshot.documents.mapNotNull {
                it.toObject(User::class.java)
            }

            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe user changes in real-time
     */
    fun observeUser(userId: String): Flow<User?> = flow {
        usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val user = snapshot?.toObject(User::class.java)
                // Note: Flow emission would be handled by callback conversion
            }
    }

    // ==================== UPDATE ====================

    /**
     * Update entire user (merge)
     */
    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            usersCollection.document(user.userId)
                .set(user, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update specific fields
     */
    suspend fun updateFields(userId: String, updates: Map<String, Any?>): Result<Unit> {
        return try {
            val validUpdates = updates.toMutableMap()
            validUpdates["updated_at"] = FieldValue.serverTimestamp()

            usersCollection.document(userId)
                .update(validUpdates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update profile
     */
    suspend fun updateProfile(
        userId: String,
        firstName: String? = null,
        lastName: String? = null,
        bio: String? = null,
        avatar: String? = null
    ): Result<Unit> {
        val updates = buildMap {
            firstName?.let { put("first_name", it) }
            lastName?.let { put("last_name", it) }
            bio?.let { put("bio", it) }
            avatar?.let { put("avatar", it) }
        }
        return updateFields(userId, updates)
    }

    /**
     * Update presence
     */
    suspend fun updatePresence(userId: String, presence: Int): Result<Unit> {
        return updateFields(userId, mapOf(
            "presence" to presence,
            "last_active" to System.currentTimeMillis()
        ))
    }

    /**
     * Update typing status
     */
    suspend fun updateTypingStatus(userId: String, chatId: String?): Result<Unit> {
        return updateFields(userId, mapOf("typing_in" to chatId))
    }

    /**
     * Update last active timestamp
     */
    suspend fun updateLastActive(userId: String): Result<Unit> {
        return updateFields(userId, mapOf(
            "last_active" to System.currentTimeMillis()
        ))
    }

    /**
     * Update FCM token
     */
    suspend fun updateFcmToken(userId: String, token: String): Result<Unit> {
        return updateFields(userId, mapOf("fcm_token" to token))
    }

    /**
     * Update privacy settings
     */
    suspend fun updatePrivacy(userId: String, privacy: Privacy): Result<Unit> {
        return updateFields(userId, mapOf("privacy" to privacy))
    }

    /**
     * Update account status
     */
    suspend fun updateAccountStatus(userId: String, status: Int): Result<Unit> {
        return updateFields(userId, mapOf("account_status" to status))
    }

    /**
     * Set premium status
     */
    suspend fun setPremium(userId: String, premium: Boolean, expiresAt: Long? = null): Result<Unit> {
        return updateFields(userId, mapOf(
            "premium" to premium,
            "premium_expires" to expiresAt
        ))
    }

    /**
     * Block user
     */
    suspend fun blockUser(userId: String, targetUserId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("blocked_users", FieldValue.arrayUnion(targetUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unblock user
     */
    suspend fun unblockUser(userId: String, targetUserId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("blocked_users", FieldValue.arrayRemove(targetUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mute chat
     */
    suspend fun muteChat(userId: String, chatId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("muted_chats", FieldValue.arrayUnion(chatId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unmute chat
     */
    suspend fun unmuteChat(userId: String, chatId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("muted_chats", FieldValue.arrayRemove(chatId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add privacy exception
     */
    suspend fun addPrivacyException(
        userId: String,
        targetUserId: String,
        field: String = "last_seen_exceptions"
    ): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("privacy.$field", FieldValue.arrayUnion(targetUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove privacy exception
     */
    suspend fun removePrivacyException(
        userId: String,
        targetUserId: String,
        field: String = "last_seen_exceptions"
    ): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("privacy.$field", FieldValue.arrayRemove(targetUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== DELETE ====================

    /**
     * Soft delete user (mark as deleted)
     */
    suspend fun softDeleteUser(userId: String): Result<Unit> {
        return updateFields(userId, mapOf(
            "account_status" to AccountStatus.DELETED,
            "presence" to Presence.OFFLINE
        ))
    }

    /**
     * Hard delete user (permanent)
     */
    suspend fun deleteUser(userId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch delete users
     */
    suspend fun deleteUsers(userIds: List<String>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            userIds.forEach { userId ->
                batch.delete(usersCollection.document(userId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== BATCH OPERATIONS ====================

    /**
     * Batch update presence for multiple users
     */
    suspend fun batchUpdatePresence(userPresences: Map<String, Int>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val timestamp = System.currentTimeMillis()

            userPresences.forEach { (userId, presence) ->
                batch.update(
                    usersCollection.document(userId),
                    mapOf(
                        "presence" to presence,
                        "last_active" to timestamp
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== UTILITY ====================

    /**
     * Check if username exists
     */
    suspend fun isUsernameAvailable(username: String): Result<Boolean> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()

            Result.success(snapshot.isEmpty)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if user exists
     */
    suspend fun userExists(userId: String): Result<Boolean> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            Result.success(snapshot.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get online users count
     */
    suspend fun getOnlineUsersCount(): Result<Int> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("presence", Presence.ONLINE)
                .get()
                .await()

            Result.success(snapshot.size())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get premium users
     */
    suspend fun getPremiumUsers(limit: Int = 100): Result<List<User>> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("premium", true)
                .limit(limit.toLong())
                .get()
                .await()

            val users = snapshot.documents.mapNotNull {
                it.toObject(User::class.java)
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ==================== USAGE EXAMPLES ====================

/*

// Initialize repository
val userRepo = UserRepository()

// Create user
val newUser = User(
    userId = "user123",
    firstName = "John",
    lastName = "Doe",
    email = "john@example.com"
)
val result = userRepo.createUser(newUser)

// Get user
val user = userRepo.getUser("user123")

// Update profile
userRepo.updateProfile(
    userId = "user123",
    firstName = "John Updated",
    bio = "New bio"
)

// Update presence
userRepo.updatePresence("user123", Presence.ONLINE)

// Set typing status
userRepo.updateTypingStatus("user123", "chat456")

// Block user
userRepo.blockUser("user123", "user789")

// Soft delete
userRepo.softDeleteUser("user123")

// Hard delete
userRepo.deleteUser("user123")

// Search users
val searchResults = userRepo.searchUsers("john", limit = 10)

// Check username availability
val isAvailable = userRepo.isUsernameAvailable("john_doe")

*/