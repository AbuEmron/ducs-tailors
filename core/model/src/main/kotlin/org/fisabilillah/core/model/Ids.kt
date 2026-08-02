package org.fisabilillah.core.model

import kotlinx.serialization.Serializable

/**
 * Typed identifiers.
 *
 * Every entity gets its own identifier type so that a [UserId] can never be passed
 * where an [OrganizationId] is expected. In a platform where a mix-up means showing
 * one person's private records to another, the compiler is the cheapest reviewer we have.
 */
@Serializable
@JvmInline
public value class UserId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class OrganizationId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class CommunityId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ConversationId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class MessageId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ListingId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ProjectId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class TaskId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class RequestId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ApplicationId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class EnrollmentId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class CommitmentId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class IntroductionId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class TrustedContactId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ReportId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ModerationCaseId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ModerationActionId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class AppealId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class RestrictionId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class CampaignId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class DonationId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class NotificationId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class QualificationId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class AuditLogId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class SkillId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class ConsentRecordId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class VerificationRequestId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class SafetySignalId(public val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
public value class LiveSessionId(public val value: String) {
    override fun toString(): String = value
}
