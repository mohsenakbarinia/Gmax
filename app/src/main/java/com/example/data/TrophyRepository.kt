package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class TrophyRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    fun observeStats(): Flow<UserProfileStats> = flow {
        val uid = requireUserId()
        val path = "users/$uid/profile/stats"
        emitAll(
            db.collection("users").document(uid)
                .collection("profile").document("stats")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfileStats::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserProfileStats(userId = uid)
                    } else {
                        UserProfileStats(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeTrophies(): Flow<List<TrophyRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/trophies"
        emitAll(
            db.collection("users").document(uid)
                .collection("trophies")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TrophyRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordHarvest(
        event: HarvestEvent,
        currentStats: UserProfileStats,
        contractCompleted: Boolean
    ): Result<Unit> {
        var activePath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trophies"
        return try {
            val uid = requireUserId()
            val clampedDistance = event.distanceMeters.coerceIn(1, 1500)
            val clampedScore = event.scorePoints.coerceIn(10, 10000)

            val trophyRef = db.collection("users").document(uid)
                .collection("trophies").document()
            activePath = trophyRef.path
            val trophyPayload = mapOf(
                "userId" to uid,
                "species" to event.species.name,
                "weapon" to event.weapon.name,
                "hitZone" to event.hitZone.name,
                "rating" to event.rating.name,
                "distanceMeters" to clampedDistance,
                "scorePoints" to clampedScore,
                "weatherPreset" to event.weatherPreset.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            trophyRef.set(trophyPayload).await()

            val statsRef = db.collection("users").document(uid)
                .collection("profile").document("stats")
            activePath = statsRef.path

            val newHarvests = (currentStats.totalHarvests + 1L).coerceIn(0L, 1_000_000L)
            val addedGold = if (event.rating == TrophyRating.GOLD) 1L else 0L
            val newGold = (currentStats.goldTrophies + addedGold).coerceIn(0L, newHarvests)
            val newLongest = if (event.hitZone.vital) {
                max(currentStats.longestVitalShotMeters, clampedDistance.toLong()).coerceIn(0L, 2000L)
            } else {
                currentStats.longestVitalShotMeters.coerceIn(0L, 2000L)
            }
            val newCompleted = (currentStats.completedContractsCount + if (contractCompleted) 1L else 0L)
                .coerceIn(0L, 100_000L)
            val newActiveIdx = ((currentStats.activeContractIndex + if (contractCompleted) 1L else 0L) % 1000L)
                .coerceIn(0L, 1000L)

            val existingSnap = statsRef.get().await()
            if (existingSnap.exists()) {
                statsRef.update(
                    mapOf(
                        "totalHarvests" to newHarvests,
                        "goldTrophies" to newGold,
                        "longestVitalShotMeters" to newLongest,
                        "completedContractsCount" to newCompleted,
                        "activeContractIndex" to newActiveIdx,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                statsRef.set(
                    mapOf(
                        "userId" to uid,
                        "totalHarvests" to newHarvests,
                        "goldTrophies" to newGold,
                        "longestVitalShotMeters" to newLongest,
                        "completedContractsCount" to newCompleted,
                        "activeContractIndex" to newActiveIdx,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, activePath)
            Result.failure(e)
        }
    }

    suspend fun deleteTrophy(trophyId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trophies/$trophyId"
        return try {
            val uid = requireUserId()
            require(trophyId.isNotBlank()) { "Trophy ID must not be blank" }
            val ref = db.collection("users").document(uid)
                .collection("trophies").document(trophyId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
