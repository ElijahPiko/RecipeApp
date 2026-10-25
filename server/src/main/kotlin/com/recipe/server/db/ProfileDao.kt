package com.recipe.server.db

import com.recipe.core.model.UserProfile

/**
 * 个人资料现在和账号是同一行（`users` 表）：账号存在，资料就存在。
 *
 * 没有独立的 profile 表了，所以 [save] 只更新、不再插入 —— 用不存在的 userId 保存等于什么也没存。
 */
interface ProfileDao {
    fun get(userId: Long): UserProfile
    fun save(userId: Long, profile: UserProfile): UserProfile
}

class MySqlProfileDao(private val db: ConnectionProvider) : ProfileDao {

    override fun get(userId: Long): UserProfile = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT nickname, avatar_url, signature, location, favorite_category FROM users WHERE id = ?"
        ).use { ps ->
            ps.setLong(1, userId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    UserProfile(
                        id = userId,
                        nickname = rs.getString("nickname")?.takeIf { it.isNotBlank() } ?: "美食家",
                        avatarUrl = rs.getString("avatar_url") ?: "",
                        signature = rs.getString("signature") ?: "记录每一道好菜",
                        location = rs.getString("location") ?: "",
                        favoriteCategory = rs.getString("favorite_category") ?: ""
                    )
                } else {
                    UserProfile(id = userId)
                }
            }
        }
    }

    override fun save(userId: Long, profile: UserProfile): UserProfile {
        db.withConnection { connection ->
            connection.prepareStatement(
                "UPDATE users SET nickname = ?, avatar_url = ?, signature = ?, location = ?, " +
                    "favorite_category = ? WHERE id = ?"
            ).use { ps ->
                ps.setString(1, profile.nickname)
                ps.setString(2, profile.avatarUrl)
                ps.setString(3, profile.signature)
                ps.setString(4, profile.location)
                ps.setString(5, profile.favoriteCategory)
                ps.setLong(6, userId)
                ps.executeUpdate()
            }
        }
        return get(userId)
    }
}

/**
 * 内存版同样合并：资料存在 [InMemoryUserDao] 的账号行上，而不是单独一个 map。
 * 这样「注册填的昵称能出现在个人页」这类行为才和 MySQL 版一致。
 */
class InMemoryProfileDao(private val users: InMemoryUserDao) : ProfileDao {

    override fun get(userId: Long): UserProfile {
        val row = users.findById(userId) ?: return UserProfile(id = userId)
        return UserProfile(
            id = userId,
            nickname = row.nickname.ifBlank { "美食家" },
            avatarUrl = row.avatarUrl,
            signature = row.signature.ifBlank { "记录每一道好菜" },
            location = row.location,
            favoriteCategory = row.favoriteCategory
        )
    }

    override fun save(userId: Long, profile: UserProfile): UserProfile {
        users.updateProfile(userId, profile)
        return get(userId)
    }
}
