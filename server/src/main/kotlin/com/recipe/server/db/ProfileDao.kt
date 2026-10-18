package com.recipe.server.db

import com.recipe.core.model.UserProfile
import java.sql.Connection

/**
 * The profile is per user. Rows written before the multi-user migration carry
 * `user_id = 0` and are simply shadowed once a real account saves its own profile.
 */
interface ProfileDao {
    fun get(userId: Long): UserProfile
    fun save(userId: Long, profile: UserProfile): UserProfile
}

class MySqlProfileDao(private val connection: Connection) : ProfileDao {

    override fun get(userId: Long): UserProfile =
        connection.prepareStatement(
            "SELECT user_id, nickname, avatar_url, signature, location, favorite_category FROM profile WHERE user_id = ?"
        ).use { ps ->
            ps.setLong(1, userId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    UserProfile(
                        id = rs.getLong("user_id"),
                        nickname = rs.getString("nickname") ?: "美食家",
                        avatarUrl = rs.getString("avatar_url") ?: "",
                        signature = rs.getString("signature") ?: "",
                        location = rs.getString("location") ?: "",
                        favoriteCategory = rs.getString("favorite_category") ?: ""
                    )
                } else {
                    UserProfile(id = userId)
                }
            }
        }

    override fun save(userId: Long, profile: UserProfile): UserProfile {
        // Update first; fall back to insert when this user has no row yet.
        val updated = connection.prepareStatement(
            "UPDATE profile SET nickname = ?, avatar_url = ?, signature = ?, location = ?, favorite_category = ? WHERE user_id = ?"
        ).use { ps ->
            ps.setString(1, profile.nickname)
            ps.setString(2, profile.avatarUrl)
            ps.setString(3, profile.signature)
            ps.setString(4, profile.location)
            ps.setString(5, profile.favoriteCategory)
            ps.setLong(6, userId)
            ps.executeUpdate()
        }
        if (updated == 0) {
            connection.prepareStatement(
                "INSERT INTO profile (user_id, nickname, avatar_url, signature, location, favorite_category) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setLong(1, userId)
                ps.setString(2, profile.nickname)
                ps.setString(3, profile.avatarUrl)
                ps.setString(4, profile.signature)
                ps.setString(5, profile.location)
                ps.setString(6, profile.favoriteCategory)
                ps.executeUpdate()
            }
        }
        return profile.copy(id = userId)
    }
}
