package com.ojashealing.family.ui.notify

import android.content.res.Resources
import com.ojashealing.family.R

/**
 * The words for a notification, in the app language: a headline and the detail under it. The server sends
 * only what happened ([type]) and the user content ([who], [body]); the same wording is used in the phone's
 * tray and in the bell list. Null for a type this version does not know.
 */
fun notificationText(res: Resources, type: String, who: String, body: String, count: Int = 1, video: Boolean = false): Pair<String, String>? {
    val others = count - 1
    return when (type) {
        "announcement" -> res.getString(R.string.push_announcement) to who.ifEmpty { body }
        "thought" -> res.getString(R.string.push_thought) to who.ifEmpty { body }
        "question" -> res.getString(R.string.push_question, who) to body
        "answer" -> res.getString(R.string.push_answer, who) to body
        "mention" -> res.getString(R.string.push_mention, who) to body
        "daily" -> res.getString(R.string.push_daily, who) to body
        "dailyReply" -> res.getString(R.string.push_daily_reply, who) to body
        "commentReply" -> res.getString(R.string.push_comment_reply, who) to body
        "like" -> (if (others > 0) res.getQuantityString(R.plurals.push_like_many, others, who, others) else res.getString(R.string.push_like, who)) to body
        "commentLike" -> (if (others > 0) res.getQuantityString(R.plurals.push_comment_like_many, others, who, others) else res.getString(R.string.push_comment_like, who)) to body
        "chat" -> res.getString(R.string.push_chat, who) to body
        "missedCall" -> res.getString(if (video) R.string.push_missed_video_call else R.string.push_missed_call, who) to res.getString(R.string.push_missed_call_body)
        "call" -> res.getString(if (video) R.string.push_video_call else R.string.push_call, who) to res.getString(R.string.call_tap_to_answer)
        // Scheduled calls: body is the time ("7 Nov, 4:30 pm"), who the other person
        "callRequest" -> res.getString(R.string.push_call_request, who) to body
        "callRequest_confirmed" -> res.getString(R.string.push_call_confirmed) to body
        "callRequest_proposed" -> res.getString(R.string.push_call_proposed) to body
        "callRequest_declined" -> res.getString(R.string.push_call_declined) to body
        "callRequest_cancelled" -> res.getString(R.string.push_call_cancelled, who) to body
        "callReminder_soon" -> res.getString(if (video) R.string.push_call_soon_video else R.string.push_call_soon, who.ifEmpty { res.getString(R.string.role_swamiji) }) to body
        "callReminder_now" -> res.getString(if (video) R.string.push_call_now_video else R.string.push_call_now) to res.getString(R.string.push_call_now_body, body)
        "goalApproved" -> res.getString(R.string.push_goal_approved, body) to res.getString(R.string.push_goal_approved_body)
        "goalRejected" -> res.getString(R.string.push_goal_rejected, body) to res.getString(R.string.push_goal_rejected_body)
        "faceApproved" -> res.getString(R.string.push_face_approved) to res.getString(R.string.face_photo_approved)
        "faceRejected" -> res.getString(R.string.push_face_rejected) to res.getString(R.string.face_photo_rejected)
        else -> null
    }
}
