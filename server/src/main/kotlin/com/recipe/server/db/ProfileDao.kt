package com.recipe.server.db

import com.recipe.core.model.UserProfile
import java.sql.Connection

interface ProfileDao {
    fun get(): UserProfile
    fun save(profile: UserProfile): UserProfile
}

class MySqlProfileDao(private val connection: Connection) : ProfileDao {

    override fun get(): UserProfile =
        connection.prepareStatement(
            "SELECT id, nickname, avatar_url, signature, location, favorite_category FROM profile WHERE id = 1"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    UserProfile(
                        id = rs.getLong("id"),
                        nickname = rs.getString("nickname") ?: "美食家",
                        avatarUrl = rs.getString("avatar_url") ?: "",
                        signature = rs.getString("signature") ?: "",
                        location = rs.getString("location") ?: "",
                        favoriteCategory = rs.getString("favorite_category") ?: ""
                    )
                } else {
                    UserProfile()
                }
            }
        }

    override fun save(profile: UserProfile): UserProfile {
        connection.prepareStatement(
            "INSERT INTO profile (id, nickname, avatar_url, signature, location, favorite_category) VALUES (1, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar_url=VALUES(avatar_url), signature=VALUES(signature), location=VALUES(location), favorite_category=VALUES(favorite_category)"
        ).use { ps ->
            ps.setString(1, profile.nickname)
            ps.setString(2, profile.avatarUrl)
            ps.setString(3, profile.signature)
            ps.setString(4, profile.location)
            ps.setString(5, profile.favoriteCategory)
            ps.executeUpdate()
        }
        return profile.copy(id = 1)
    }
}
