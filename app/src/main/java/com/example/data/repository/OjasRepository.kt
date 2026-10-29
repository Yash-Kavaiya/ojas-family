package com.example.data.repository

import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OjasRepository {

    private val _userProfile = MutableStateFlow(
        UserProfile(
            id = "user_001",
            name = "Yash Anand",
            photoUrl = "",
            dob = "1997-04-18",
            gender = "Spiritual Seeker",
            country = "India",
            state = "Gujarat",
            city = "Ahmedabad",
            mokshaPoints = 19,
            daysActive = 14,
            userLevel = UserLevel.LEVEL_2,
            paidLevel = PaidMemberLevel.LEVEL_1,
            isAdmin = false,
            isProfileHidden = false,
            areGoalsHidden = false,
            streakDays = 14,
            lastDailyLoginRewardDate = "2026-10-28",
            notifications = NotificationSettings()
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _thoughts = MutableStateFlow(initialThoughts())
    val thoughts: StateFlow<List<ThoughtOfDay>> = _thoughts.asStateFlow()

    private val _questions = MutableStateFlow(initialQuestions())
    val questions: StateFlow<List<CommunityQuestion>> = _questions.asStateFlow()

    private val _dailyActivities = MutableStateFlow(initialDailyActivities())
    val dailyActivities: StateFlow<List<DailyActivityPost>> = _dailyActivities.asStateFlow()

    private val _announcements = MutableStateFlow(initialAnnouncements())
    val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    private val _goals = MutableStateFlow(initialGoals())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _activities = MutableStateFlow(initialSpiritualActivities())
    val activities: StateFlow<List<SpiritualActivityItem>> = _activities.asStateFlow()

    private val _understandingTopics = MutableStateFlow(initialUnderstanding())
    val understandingTopics: StateFlow<List<UnderstandingTopic>> = _understandingTopics.asStateFlow()

    private val _chatMessages = MutableStateFlow(initialChat())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _scheduledCalls = MutableStateFlow(initialCalls())
    val scheduledCalls: StateFlow<List<PersonalCallBooking>> = _scheduledCalls.asStateFlow()

    // Trigger for the Paytm coin burst animation: (amount, message, timestamp)
    data class RewardEvent(val amount: Int, val reason: String, val timestamp: Long = System.currentTimeMillis())
    private val _rewardEvent = MutableStateFlow<RewardEvent?>(null)
    val rewardEvent: StateFlow<RewardEvent?> = _rewardEvent.asStateFlow()

    fun clearRewardEvent() {
        _rewardEvent.value = null
    }

    fun triggerReward(amount: Int, reason: String) {
        _userProfile.update { current ->
            val newPoints = current.mokshaPoints + amount
            current.copy(mokshaPoints = newPoints)
        }
        _rewardEvent.value = RewardEvent(amount, reason)
    }

    // Daily Open Login Reward (+1 Moksha Point per day on free app)
    fun checkDailyLoginReward(todayDateString: String = "2026-10-29") {
        val current = _userProfile.value
        if (current.lastDailyLoginRewardDate != todayDateString) {
            _userProfile.update {
                it.copy(
                    lastDailyLoginRewardDate = todayDateString,
                    mokshaPoints = it.mokshaPoints + 1,
                    streakDays = it.streakDays + 1
                )
            }
            _rewardEvent.value = RewardEvent(1, "Daily Spiritual Presence Reward")
        }
    }

    // Like Thought of the Day:
    // Only current date like (dayOffset == 0) gets 1 Moksha Point
    fun toggleLikeThought(thoughtId: String): Boolean {
        val profile = _userProfile.value
        if (!profile.canLikePosts()) return false

        var pointEarned = false
        _thoughts.update { list ->
            list.map { thought ->
                if (thought.id == thoughtId) {
                    val newIsLiked = !thought.isLiked
                    val newCount = if (newIsLiked) thought.likesCount + 1 else (thought.likesCount - 1).coerceAtLeast(0)
                    if (newIsLiked && thought.dayOffset == 0) {
                        pointEarned = true
                    }
                    thought.copy(isLiked = newIsLiked, likesCount = newCount)
                } else thought
            }
        }

        if (pointEarned) {
            triggerReward(1, "+1 Moksha Point for Today's Thought!")
        }
        return true
    }

    // Add comment to Thought of the Day
    fun addCommentToThought(thoughtId: String, text: String, imageUrl: String? = null, linkUrl: String? = null): Boolean {
        val profile = _userProfile.value
        if (!profile.canCommentPosts()) return false

        val newComment = ThoughtComment(
            id = "c_${System.currentTimeMillis()}",
            authorName = profile.name,
            authorLevel = profile.userLevel.title,
            text = text,
            date = "Just now",
            imageUrl = imageUrl,
            linkUrl = linkUrl
        )

        _thoughts.update { list ->
            list.map { thought ->
                if (thought.id == thoughtId) {
                    thought.copy(comments = listOf(newComment) + thought.comments)
                } else thought
            }
        }

        triggerReward(1, "+1 Moksha Point for Thought Satsang!")
        return true
    }

    // Community Question Like
    fun toggleLikeQuestion(questionId: String): Boolean {
        val profile = _userProfile.value
        if (!profile.canLikePosts()) return false

        var pointEarned = false
        _questions.update { list ->
            list.map { q ->
                if (q.id == questionId) {
                    val newLiked = !q.isLiked
                    val count = if (newLiked) q.likesCount + 1 else (q.likesCount - 1).coerceAtLeast(0)
                    if (newLiked) pointEarned = true
                    q.copy(isLiked = newLiked, likesCount = count)
                } else q
            }
        }
        if (pointEarned) {
            triggerReward(1, "+1 Moksha Point for Uplifting Community!")
        }
        return true
    }

    // Ask Question in Community
    fun askQuestion(title: String, body: String, tags: List<String>, imageUrl: String? = null): Boolean {
        val profile = _userProfile.value
        if (!profile.canAskQuestions()) return false

        val newQ = CommunityQuestion(
            id = "q_${System.currentTimeMillis()}",
            title = title,
            body = body,
            authorName = profile.name,
            authorLevel = profile.userLevel.title,
            date = "Just now",
            tags = tags,
            answers = emptyList(),
            likesCount = 0,
            isLiked = false,
            isSwamijiAnswered = false,
            imageUrl = imageUrl
        )

        _questions.update { listOf(newQ) + it }
        triggerReward(1, "+1 Moksha Point for Spiritual Inquiry!")
        return true
    }

    // Answer Question in Community
    fun answerQuestion(questionId: String, answerText: String) {
        val profile = _userProfile.value
        val role = if (profile.isAdmin) "Swami Anand Swaroop" else "Community Member"
        val answer = CommunityAnswer(
            id = "ans_${System.currentTimeMillis()}",
            questionId = questionId,
            authorName = profile.name,
            authorRole = role,
            isSwamijiAnswer = profile.isAdmin,
            text = answerText,
            date = "Just now"
        )

        _questions.update { list ->
            list.map { q ->
                if (q.id == questionId) {
                    q.copy(
                        answers = q.answers + answer,
                        isSwamijiAnswered = q.isSwamijiAnswered || profile.isAdmin
                    )
                } else q
            }
        }
        triggerReward(1, "+1 Moksha Point for Sharing Wisdom!")
    }

    // Daily Activities Post
    fun addDailyActivityPost(title: String, description: String, category: String, imageUrl: String? = null): Boolean {
        val profile = _userProfile.value
        if (!profile.canPostDailyActivities()) return false

        val newPost = DailyActivityPost(
            id = "act_${System.currentTimeMillis()}",
            authorName = profile.name,
            authorLevel = profile.paidLevel.title,
            title = title,
            description = description,
            category = category,
            imageUrl = imageUrl,
            date = "Just now",
            likesCount = 0,
            isLiked = false,
            commentsCount = 0
        )
        _dailyActivities.update { listOf(newPost) + it }
        triggerReward(5, "+5 Moksha Points for Living Yoga in Daily Life!")
        return true
    }

    // Like Daily Activity Post
    fun toggleLikeDailyActivity(postId: String): Boolean {
        val profile = _userProfile.value
        if (!profile.canLikePosts()) return false

        var pointEarned = false
        _dailyActivities.update { list ->
            list.map { p ->
                if (p.id == postId) {
                    val newLiked = !p.isLiked
                    val count = if (newLiked) p.likesCount + 1 else (p.likesCount - 1).coerceAtLeast(0)
                    if (newLiked) pointEarned = true
                    p.copy(isLiked = newLiked, likesCount = count)
                } else p
            }
        }
        if (pointEarned) {
            triggerReward(1, "+1 Moksha Point for Celebrating Member's Seva!")
        }
        return true
    }

    // Complete Goal
    fun completeGoal(goalId: String, note: String? = null, photoUrl: String? = null) {
        var reward = 0
        _goals.update { list ->
            list.map { g ->
                if (g.id == goalId) {
                    reward = g.mokshaReward
                    g.copy(
                        isAchieved = true,
                        progressDays = g.targetDays,
                        proofNote = note ?: "Achieved with joyful devotion",
                        proofImageUrl = photoUrl,
                        achievedDate = "29 Oct 2026"
                    )
                } else g
            }
        }
        if (reward > 0) {
            triggerReward(reward, "+$reward Moksha Points for Goal Realization! 🌟")
        }
    }

    // Update Profile
    fun updateProfile(
        name: String,
        dob: String,
        gender: String,
        country: String,
        state: String,
        city: String
    ) {
        _userProfile.update {
            it.copy(
                name = name,
                dob = dob,
                gender = gender,
                country = country,
                state = state,
                city = city
            )
        }
    }

    fun toggleProfileHidden(hidden: Boolean) {
        _userProfile.update { it.copy(isProfileHidden = hidden) }
    }

    fun toggleGoalsHidden(hidden: Boolean) {
        _userProfile.update { it.copy(areGoalsHidden = hidden) }
    }

    fun updateNotifications(newSettings: NotificationSettings) {
        _userProfile.update { it.copy(notifications = newSettings) }
    }

    // Admin / Level Simulator Switcher (for testing user levels and paid member levels easily)
    fun setUserLevel(level: UserLevel) {
        _userProfile.update { it.copy(userLevel = level) }
    }

    fun setPaidLevel(paid: PaidMemberLevel) {
        _userProfile.update { it.copy(paidLevel = paid) }
    }

    fun setAdmin(isAdmin: Boolean) {
        _userProfile.update { it.copy(isAdmin = isAdmin) }
    }

    // Chat Message
    fun sendChatMessage(text: String) {
        val userMsg = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            senderName = _userProfile.value.name,
            isFromSwamiji = false,
            text = text,
            timestamp = "Just now"
        )
        _chatMessages.update { it + userMsg }

        // Swamiji's thoughtful spiritual response simulation
        val replyText = generateSwamijiResponse(text)
        val swamijiMsg = ChatMessage(
            id = "swami_${System.currentTimeMillis() + 100}",
            senderName = "Swami Anand Swaroop",
            isFromSwamiji = true,
            text = replyText,
            timestamp = "Just now"
        )
        _chatMessages.update { it + swamijiMsg }
    }

    private fun generateSwamijiResponse(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("energy") || lower.contains("chakra") ->
                "Hari Om Anand. When you feel a drop in energy, bring attention to your breath at the Navel (Manipura). Do 5 minutes of Box Breathing. You will feel an instant surge of Ojas."
            lower.contains("fear") || lower.contains("protection") || lower.contains("worry") ->
                "Blessings to you. The Divine Blue Healing Light envelops your aura at all times. Fear is merely forgotten connection. Visualize a radiant oval shield of cobalt blue around your body."
            lower.contains("attraction") || lower.contains("manifest") ->
                "Remember, the Law of Attraction works not through yearning, but through gratitude in the present moment. Feel the fulfilled joy before the form arrives."
            else ->
                "Hari Om! Beautiful spiritual sincerity, dear soul. Live each moment today with conscious awareness, aligning your thought, speech, and action. Blessings always."
        }
    }

    // Schedule Personal Call
    fun scheduleCall(planName: String, scheduledTime: String) {
        val booking = PersonalCallBooking(
            id = "call_${System.currentTimeMillis()}",
            planName = planName,
            scheduledTime = scheduledTime,
            status = "Confirmed"
        )
        _scheduledCalls.update { listOf(booking) + it }
        triggerReward(10, "+10 Moksha Points for Seeking Satsang!")
    }

    // Admin schedule Thought of the Day
    fun scheduleThought(title: String, quote: String, commentary: String, chakra: String, mantra: String) {
        val newThought = ThoughtOfDay(
            id = "sch_${System.currentTimeMillis()}",
            dateLabel = "Scheduled Post (Next Month)",
            dayOffset = -1,
            title = title,
            quote = quote,
            swamijiCommentary = commentary,
            chakraAlignment = chakra,
            mantra = mantra,
            likesCount = 0,
            isLiked = false,
            comments = emptyList(),
            isScheduled = true
        )
        _thoughts.update { listOf(newThought) + it }
    }

    // Initial Seed Data
    private fun initialThoughts(): List<ThoughtOfDay> {
        return listOf(
            ThoughtOfDay(
                id = "t_0",
                dateLabel = "Today • 29 Oct",
                dayOffset = 0,
                title = "Awakening the Inner Sun",
                quote = "Joy is not something you acquire from the world. It is the natural radiant fragrance of an aligned chakra system.",
                swamijiCommentary = "When we clear the stagnant energies accumulated in day-to-day household interactions, the Solar Plexus (Manipura) burns brightly with Ojas. You become a giver of peace rather than a seeker of approval.",
                chakraAlignment = "Manipura (Solar Plexus) • Radiant Golden Fire",
                mantra = "Om Ram Namaha",
                likesCount = 142,
                isLiked = false,
                comments = listOf(
                    ThoughtComment("c1", "Bhavin Patel", "Level 3 (Sadhak)", "Swamiji, doing the morning box breathing aligned my stomach instantly!", "2 hours ago"),
                    ThoughtComment("c2", "Meera Sharma", "Paid Member Level 3", "Felt deep golden light during morning meditation today. Hari Om!", "4 hours ago")
                )
            ),
            ThoughtOfDay(
                id = "t_1",
                dateLabel = "Yesterday • 28 Oct",
                dayOffset = 1,
                title = "The Shield of Compassion",
                quote = "True energy protection is not building walls of fear, but expanding your aura with such intense loving-kindness that negativity dissolves on contact.",
                swamijiCommentary = "Practice the Quick Protection visualization before stepping out into crowded places. When your heart chakra radiates unconditional goodwill, lower astral vibrations cannot touch you.",
                chakraAlignment = "Anahata (Heart Chakra) • Luminous Emerald Light",
                mantra = "Om Yam Namaha",
                likesCount = 215,
                isLiked = true,
                comments = listOf(
                    ThoughtComment("c3", "Rajesh V.", "Level 3 (Sadhak)", "This reflection helped me resolve workplace tension peacefully.", "1 day ago")
                )
            ),
            ThoughtOfDay(
                id = "t_2",
                dateLabel = "27 Oct",
                dayOffset = 2,
                title = "Rooting in Gratitude",
                quote = "A tree can touch the skies only when its roots hold firmly to mother earth. Ground yourself in simple daily duties.",
                swamijiCommentary = "When cooking or sweeping the floor, do not see it as mundane chore. See it as sanctifying the temple of life. This transforms ordinary labor into high sadhana.",
                chakraAlignment = "Muladhara (Root Chakra) • Deep Crimson Earth",
                mantra = "Om Lam Namaha",
                likesCount = 189,
                isLiked = false,
                comments = emptyList()
            ),
            ThoughtOfDay(
                id = "t_3",
                dateLabel = "26 Oct",
                dayOffset = 3,
                title = "The Flow of Sacred Creativity",
                quote = "Resistance to life creates energetic knots in the Sacral center. Allow life to flow like water through pebbles.",
                swamijiCommentary = "Release rigid expectations of how people should behave. Flow like the holy Ganga.",
                chakraAlignment = "Svadhisthana (Sacral Chakra) • Oceanic Amber",
                mantra = "Om Vam Namaha",
                likesCount = 173,
                isLiked = false,
                comments = emptyList()
            ),
            ThoughtOfDay(
                id = "t_4",
                dateLabel = "25 Oct",
                dayOffset = 4,
                title = "Speaking the Truth with Sweetness",
                quote = "Words carry vibrational geometry. When you speak with purity, your words heal those who listen.",
                swamijiCommentary = "Vishuddha chakra aligns when gossip and complaints cease, making room for divine chanting.",
                chakraAlignment = "Vishuddha (Throat Chakra) • Celestial Sky Blue",
                mantra = "Om Ham Namaha",
                likesCount = 240,
                isLiked = true,
                comments = emptyList()
            ),
            ThoughtOfDay(
                id = "t_5",
                dateLabel = "24 Oct",
                dayOffset = 5,
                title = "Awakening Intuition in Stillness",
                quote = "Beyond the chatter of the intellect lies the whisper of the Third Eye. Listen to the silence between breaths.",
                swamijiCommentary = "Before sleeping, do the Night Protection practice to enter deep restful communion.",
                chakraAlignment = "Ajna (Third Eye Chakra) • Royal Indigo",
                mantra = "Om Ksham Namaha",
                likesCount = 198,
                isLiked = false,
                comments = emptyList()
            ),
            ThoughtOfDay(
                id = "t_6",
                dateLabel = "23 Oct",
                dayOffset = 6,
                title = "Union with the Cosmic Ocean",
                quote = "You are not a drop in the ocean; you are the entire ocean in a drop. Realize your infinite nature.",
                swamijiCommentary = "Sahasrara alignment brings effortless fulfillment and dissolves all feelings of lack.",
                chakraAlignment = "Sahasrara (Crown Chakra) • Thousand-Petaled Violet Lotus",
                mantra = "Om Namo Narayana",
                likesCount = 310,
                isLiked = false,
                comments = emptyList()
            )
        )
    }

    private fun initialQuestions(): List<CommunityQuestion> {
        return listOf(
            CommunityQuestion(
                id = "q_1",
                title = "How to clear negative energy picked up at a crowded hospital or market?",
                body = "Swamiji and family, whenever I return from crowded hospitals or markets, I feel drained and heavy around my shoulders. What is the quickest chakra cleansing process?",
                authorName = "Kavita Desai",
                authorLevel = "Level 3 (Sadhak)",
                date = "Yesterday",
                tags = listOf("Energy Cleaning", "Protection", "Aura"),
                answers = listOf(
                    CommunityAnswer(
                        id = "a_1",
                        questionId = "q_1",
                        authorName = "Swami Anand Swaroop",
                        authorRole = "Swami Anand Swaroop",
                        isSwamijiAnswer = true,
                        text = "Hari Om Anand Kavita. Wash your hands and face up to elbows with rock salt (Sendha Namak) water immediately upon returning. Then do the 3-minute Quick Protection practice in the app, commanding all foreign energies to leave your field in peace.",
                        date = "Yesterday"
                    ),
                    CommunityAnswer(
                        id = "a_2",
                        questionId = "q_1",
                        authorName = "Jignesh Shah",
                        authorRole = "Paid Member Level 3",
                        isSwamijiAnswer = false,
                        text = "Rock salt bath and lighting camphor also works wonders for me!",
                        date = "Yesterday"
                    )
                ),
                likesCount = 34,
                isLiked = false,
                isSwamijiAnswered = true
            ),
            CommunityQuestion(
                id = "q_2",
                title = "Can box breathing be practiced right after lunch or only on empty stomach?",
                body = "I want to do the morning protection practice, but sometimes I get late. Is light breathing safe after meals?",
                authorName = "Aarav Mehta",
                authorLevel = "Level 3 (Sadhak)",
                date = "2 days ago",
                tags = listOf("Box Breathing", "Sadhana", "Timing"),
                answers = listOf(
                    CommunityAnswer(
                        id = "a_3",
                        questionId = "q_2",
                        authorName = "Swami Anand Swaroop",
                        authorRole = "Swami Anand Swaroop",
                        isSwamijiAnswer = true,
                        text = "Deep retentions (holding breath) should be done only on light or empty stomach (at least 2.5 hours after food). However, slow, gentle rhythmic breathing without holding is always safe.",
                        date = "2 days ago"
                    )
                ),
                likesCount = 28,
                isLiked = false,
                isSwamijiAnswered = true
            )
        )
    }

    private fun initialDailyActivities(): List<DailyActivityPost> {
        return listOf(
            DailyActivityPost(
                id = "act_1",
                authorName = "Sonaliben Trivedi",
                authorLevel = "Paid Member Level 3",
                title = "Satvik Khichdi Prasad Cooked with Gayatri Chanting",
                description = "Prepared midday food for the family while infusing each stir with the Gayatri Mantra. Food energy felt so tranquil and nourishing! #CookAndPost #OjasEnergy",
                category = "Cooking",
                date = "3 hours ago",
                likesCount = 42,
                isLiked = false,
                commentsCount = 6
            ),
            DailyActivityPost(
                id = "act_2",
                authorName = "Hareshbhai Vora",
                authorLevel = "Paid Member Level 3",
                title = "Sanctifying the Household Temple & Cleaning Seva",
                description = "Cleaned the meditation altar and living room with eucalyptus and saltwater essence. Space feels light and vibrating with peace.",
                category = "Cleaning",
                date = "6 hours ago",
                likesCount = 38,
                isLiked = true,
                commentsCount = 4
            ),
            DailyActivityPost(
                id = "act_3",
                authorName = "Dr. Ananya Joshi",
                authorLevel = "Paid Member Level 3",
                title = "Completed 45 Minutes Surya Namaskar & Pranayama",
                description = "Day 18 of the 21-day Sadhana goal. Felt immense prana coursing through the Manipura and Heart chakras.",
                category = "Exercise & Yoga",
                date = "8 hours ago",
                likesCount = 59,
                isLiked = false,
                commentsCount = 9
            )
        )
    }

    private fun initialAnnouncements(): List<Announcement> {
        return listOf(
            Announcement(
                id = "ann_1",
                title = "Live Global Satsang & Blue Light Healing with Swamiji",
                date = "Sunday, 2 Nov • 8:00 AM IST",
                tag = "Live Satsang",
                body = "Join Swami Anand Swaroop for a special collective energy clearing session to align the 7 Body Chakras and elevate family vibration.",
                swamijiBlessing = "Come with an open heart. May your life overflow with divine Ojas."
            ),
            Announcement(
                id = "ann_2",
                title = "21-Day Energy Protection Challenge Starts Next Week",
                date = "Starting Monday • Global Ojas Playground",
                tag = "Sadhana Challenge",
                body = "Commit to 21 consecutive days of Morning Box Breathing and Night Intuition Protection to permanently seal your aura and raise Moksha Points.",
                swamijiBlessing = "Consistency is the mother of spiritual power."
            )
        )
    }

    private fun initialGoals(): List<Goal> {
        return listOf(
            Goal(
                id = "g_1",
                title = "Cook and post image",
                description = "Prepare conscious satvik food with loving remembrance and share photo proof.",
                category = "Daily Seva",
                mokshaReward = 15,
                isAchieved = true,
                progressDays = 1,
                targetDays = 1,
                proofNote = "Prepared Satvik Moong Dal Khichdi with ghee and love.",
                achievedDate = "28 Oct 2026"
            ),
            Goal(
                id = "g_2",
                title = "Wear assigned cloths and post the image",
                description = "Wear spiritual cotton/saffron/white clothes aligned with your chakra day and share photo proof.",
                category = "Aura Alignment",
                mokshaReward = 20,
                isAchieved = false,
                progressDays = 0,
                targetDays = 1
            ),
            Goal(
                id = "g_3",
                title = "Doing daily household work and post the image",
                description = "Sweep, clean, or arrange household space as an offering of Karma Yoga.",
                category = "Karma Yoga",
                mokshaReward = 15,
                isAchieved = false,
                progressDays = 0,
                targetDays = 1
            ),
            Goal(
                id = "g_4",
                title = "Doing daily yoga and post the image",
                description = "Complete at least 12 Surya Namaskars or your daily asana routine.",
                category = "Body Energy",
                mokshaReward = 25,
                isAchieved = false,
                progressDays = 0,
                targetDays = 1
            ),
            Goal(
                id = "g_5",
                title = "Do the morning practice for 21 days",
                description = "Consecutively practice the Morning Box Breathing and Protection sadhana for 21 days.",
                category = "21-Day Sadhana",
                mokshaReward = 100,
                isAchieved = false,
                progressDays = 14,
                targetDays = 21
            ),
            Goal(
                id = "g_6",
                title = "Do the morning and night practices for 21 days",
                description = "Consecutively practice both Morning and Night Protection Meditations for 21 days.",
                category = "21-Day Sadhana",
                mokshaReward = 250,
                isAchieved = false,
                progressDays = 11,
                targetDays = 21
            ),
            Goal(
                id = "g_7",
                title = "Plant a tree",
                description = "Plant a neem, peepal, tulsi, or banyan tree in nature and nurture it.",
                category = "Earth Seva",
                mokshaReward = 50,
                isAchieved = false,
                progressDays = 0,
                targetDays = 1
            ),
            Goal(
                id = "g_8",
                title = "Change suggested things for energy healing",
                description = "Replace synthetic fragrances with pure camphor/sandalwood and declutter stagnant corners.",
                category = "Vastu & Energy",
                mokshaReward = 30,
                isAchieved = false,
                progressDays = 0,
                targetDays = 1
            )
        )
    }

    private fun initialSpiritualActivities(): List<SpiritualActivityItem> {
        return listOf(
            SpiritualActivityItem(
                id = "act_blue_light",
                title = "Blue Healing Light General Meditation",
                subtitle = "Open for all seekers • Recorded Audio & Visual",
                description = "Bathe your entire subtle body in a serene sapphire blue healing light that calms nervous tension, dissolves anxiety, and harmonizes your aura.",
                category = "General Meditation",
                durationMinutes = 12,
                accessLevel = ActivityAccessLevel.OPEN_FOR_ALL,
                chakraFocus = "Vishuddha & Anahata (Heart & Throat)"
            ),
            SpiritualActivityItem(
                id = "act_quick_protection",
                title = "Quick Protection Energy Clearing",
                subtitle = "Open for all seekers • 3-Min Shield",
                description = "Instant energy cleansing technique taught by Swami Anand Swaroop to cut negative cords, clear psychic fog, and seal your electromagnetic boundary.",
                category = "Energy Protection",
                durationMinutes = 5,
                accessLevel = ActivityAccessLevel.OPEN_FOR_ALL,
                chakraFocus = "Aura Shield & Muladhara"
            ),
            SpiritualActivityItem(
                id = "act_morning_protection",
                title = "Daily Morning Protection & Meditation",
                subtitle = "Box Breathing & Energy Surge • Level 3 or Paid",
                description = "Interactive 4-4-4-4 Box Breathing cycle followed by Solar Plexus energization to charge your body with radiant Ojas for the day ahead.",
                category = "Morning Sadhana",
                durationMinutes = 15,
                accessLevel = ActivityAccessLevel.LEVEL_3_OR_PAID,
                chakraFocus = "Manipura (Solar Plexus) & Prana",
                hasBoxBreathing = true,
                allowsReminderNotification = true,
                reminderTime = "06:00 AM"
            ),
            SpiritualActivityItem(
                id = "act_night_protection",
                title = "Daily Night Protection & Meditation",
                subtitle = "Relaxation, Thanksgiving & Intuition • Level 3 or Paid",
                description = "Release the events of the day through conscious thanksgiving. Awaken the intuitive faculties of the Ajna chakra for deep restorative sleep.",
                category = "Night Sadhana",
                durationMinutes = 18,
                accessLevel = ActivityAccessLevel.LEVEL_3_OR_PAID,
                chakraFocus = "Ajna (Third Eye) & Sahasrara",
                allowsReminderNotification = true,
                reminderTime = "09:30 PM"
            ),
            SpiritualActivityItem(
                id = "act_yoga_pranayama",
                title = "Daily Yoga, Pranayama & Tio Practices",
                subtitle = "Chakra Asanas & Breath Alchemy • Level 4 or Paid",
                description = "Sacred sequence of 12 asanas synchronized with chakra awareness, Nadi Shodhana, and Tio energetic circulation.",
                category = "Yoga & Pranayama",
                durationMinutes = 25,
                accessLevel = ActivityAccessLevel.LEVEL_4_OR_PAID,
                chakraFocus = "Complete 7-Chakra System"
            ),
            SpiritualActivityItem(
                id = "act_sacred_frequencies",
                title = "Chakra Frequencies & Gayatri Tones",
                subtitle = "Sacred Sound Therapy • Level 4 or Paid",
                description = "Pure solfeggio and Vedic root frequencies (432Hz & 528Hz) to realign cellular resonance.",
                category = "Sacred Sounds",
                durationMinutes = 20,
                accessLevel = ActivityAccessLevel.LEVEL_4_OR_PAID,
                chakraFocus = "Cellular & Subtle Body"
            )
        )
    }

    private fun initialUnderstanding(): List<UnderstandingTopic> {
        return listOf(
            UnderstandingTopic(
                id = "und_1",
                title = "What is Energy Cleaning?",
                summary = "The spiritual science of removing dense emotional impressions and psychic residues.",
                fullExplanation = "Just as dirt accumulates on the physical skin and requires bathing, our subtle energy body (Pranamaya and Manomaya Koshas) constantly absorbs thoughts, emotions, and environmental frequencies from workplaces, relatives, and public spaces.\n\nEnergy cleaning is the deliberate process of wiping clean these subtle imprints before they crystallize into physical fatigue, irritability, or illness. When energy is clean, your natural Ojas (radiant vitality) flows unhindered.",
                practicalSteps = listOf(
                    "Wash hands and face with rock salt water after meeting negative environments.",
                    "Light a small camphor or cow dung dhoop at twilight to purify space.",
                    "Drink water from a copper vessel charged with peaceful intentions.",
                    "Practice 5 minutes of conscious exhalation releasing all gray fog."
                ),
                chakraImpact = "Purifies Manipura (Navel) and Anahata (Heart)",
                iconName = "water_drop"
            ),
            UnderstandingTopic(
                id = "und_2",
                title = "What is Protection?",
                summary = "Establishing an impenetrable shield of golden-blue consciousness around your aura.",
                fullExplanation = "Protection is not born out of fear of ghosts or ill-will; rather, it is recognizing that you are an open energetic vortex. Without conscious boundaries, an empathic seeker naturally absorbs others' grief and anxiety.\n\nTrue spiritual protection seals the electromagnetic torus field around your body with the high vibration of gratitude and Divine Presence, reflecting all lower vibrations back to the cosmos for transmutation.",
                practicalSteps = listOf(
                    "Before stepping out of bed, visualize a 3-foot cocoon of golden-blue light around you.",
                    "Chant the Mahamrityunjaya or Gayatri mantra with focused heart intent.",
                    "Mentally say: 'Only Divine Love enters, only Divine Blessing leaves.'",
                    "Do not engage in ungrounded arguments that tear your aura fabric."
                ),
                chakraImpact = "Fortifies Muladhara (Root) and Ajna (Shield)",
                iconName = "shield"
            ),
            UnderstandingTopic(
                id = "und_3",
                title = "Law of Attraction in Spiritual Science",
                summary = "Why you don't attract what you want, you attract what you are.",
                fullExplanation = "Modern literature often misinterprets the Law of Attraction as mental greed. Swami Anand Swaroop teaches that the Universe does not respond to wordy desires; it responds to your core energy frequency.\n\nIf you ask for wealth while feeling scarcity in your solar plexus, the universe amplifies scarcity. When you enter the state of inner fullness (Purnatva) through chakra alignment, circumstances of abundance, peace, and health effortlessly gravitate toward you.",
                practicalSteps = listOf(
                    "Shift from 'I need this' to 'I am thankful for the abundance already here'.",
                    "Align your Navel fire with purposeful action, not passive daydreaming.",
                    "Bless everyone you meet with silent goodwill; what you send returns tenfold.",
                    "Keep your surroundings clutter-free to allow fresh prana to circulate."
                ),
                chakraImpact = "Harmonizes Solar Plexus and Crown Chakras",
                iconName = "auto_awesome"
            ),
            UnderstandingTopic(
                id = "und_4",
                title = "Living the 7 Body Chakras in Day-to-Day Life",
                summary = "Practical integration of subtle energy centers in family and work life.",
                fullExplanation = "Spiritual wisdom is hollow if it cannot be lived while cooking, washing clothes, and interacting with family members. The 7 Chakras are not mystical abstractions; they correspond to our physical organs, hormonal glands, and emotional states.\n\nWhen we apply Swamiji's guidance, daily chores become joyful energy alignments.",
                practicalSteps = listOf(
                    "Root: Orderly finances and grounded home environment.",
                    "Sacral: Joyful creativity in cooking and household aesthetics.",
                    "Solar Plexus: Courage to speak kindly and set healthy boundaries.",
                    "Heart: Forgiving past family grievances.",
                    "Throat: Speaking uplifting and compassionate words.",
                    "Third Eye: Daily 10-minute meditation for quiet intuition.",
                    "Crown: Surrendering daily outcomes to the Supreme Divine."
                ),
                chakraImpact = "Full Spectrum Ojas Awakening",
                iconName = "spa"
            )
        )
    }

    private fun initialChat(): List<ChatMessage> {
        return listOf(
            ChatMessage(
                id = "c_init",
                senderName = "Swami Anand Swaroop",
                isFromSwamiji = true,
                text = "Hari Om Anand! Welcome to personal spiritual guidance. How is your daily chakra alignment feeling today? You may share your questions or difficulties in daily sadhana.",
                timestamp = "Yesterday"
            )
        )
    }

    private fun initialCalls(): List<PersonalCallBooking> {
        return listOf(
            PersonalCallBooking(
                id = "call_1",
                planName = "1-Week Guidance (3-4 Personal Calls)",
                scheduledTime = "Friday, 31 Oct • 05:00 PM IST",
                status = "Upcoming"
            )
        )
    }
}
