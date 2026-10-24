package com.deadfest.ui.screens

import com.deadfest.util.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.deadfest.R
import com.deadfest.data.model.LevelRewardCatalog
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MAX_BIO
import com.deadfest.ui.viewmodel.MainViewModel

/** Emoji avatars anyone can pick. */
private val AVATARS = listOf(
    "💀", "☠️", "🧟", "🧟‍♀️", "🧛", "👻",
    "👹", "👺", "👽", "🤖", "🎃", "🦴",
    "🕷️", "🦇", "🐺", "🐍", "🦂", "🐀",
    "🔥", "☢️", "☣️", "💣", "🪓", "🗡️",
    "🔫", "🏹", "🛡️", "⚡", "🌑", "🩸",
    "🧠", "👁️", "😈", "🤡", "😎", "🤠"
)

/** Badge colours available to everyone; level rewards add more on top. */
private val BASE_COLOURS = listOf(
    "#536DFE", "#FF4B4B", "#FFB300", "#43A047", "#00ACC1", "#8E24AA", "#EC407A", "#78909C"
)

@Composable
fun EditProfileScreen(navController: NavController, viewModel: MainViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val rewards by viewModel.levelRewards.collectAsStateWithLifecycle()
    val p = user?.playerData

    // Seed the form once from the profile; later profile updates must not wipe what's being typed.
    var avatar by rememberSaveableString(p?.avatar ?: AVATARS.first())
    var bio by rememberSaveableString(p?.bio ?: "")
    var color by rememberSaveableString(p?.color ?: BASE_COLOURS.first())

    val unlockedColours = remember(rewards, p?.level, p?.prestigeCount) {
        rewards.filter { it.type == "color" && LevelRewardCatalog.isUnlocked(it, p?.level ?: 1, p?.prestigeCount ?: 0) }
            .map { it.value }
    }
    val colours = remember(unlockedColours) { (BASE_COLOURS + unlockedColours).distinctBy { it.uppercase() } }
    val changed = p != null && (avatar != (p.avatar ?: "") || bio.trim() != (p.bio ?: "") || !color.equals(p.color ?: "", true))

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_subtitle),
            onBack = { navController.popBackStack() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // ---- live preview
            SurfaceCard(accent = parseThemeColor(color), modifier = Modifier.enterOnMount(0)) {
                Text(stringResource(R.string.profile_preview), color = TextLow, style = DeadFestType.microLabel)
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FramedAvatar(avatar, p?.avatarFrame, color, 60)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(user?.handle ?: "", color = TextHi, style = DeadFestType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((p?.title ?: tr(R.string.admin_survivor)).uppercase(), color = parseThemeColor(color), style = DeadFestType.microLabel)
                        if (bio.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(bio.trim(), color = TextMid, style = DeadFestType.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ---- avatar grid
            SectionLabel(stringResource(R.string.profile_avatar))
            SurfaceCard {
                AVATARS.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { emoji ->
                            val selected = emoji == avatar
                            val cd = stringResource(R.string.cd_avatar_option, emoji)
                            Box(
                                Modifier
                                    .size(48.dp) // 48dp: minimum touch target
                                    .clip(CircleShape)
                                    .background(if (selected) Ember.copy(alpha = 0.18f) else Color.Transparent)
                                    .border(if (selected) 2.dp else 0.dp, if (selected) Ember else Color.Transparent, CircleShape)
                                    .pressClick(CircleShape) { avatar = emoji }
                                    .semantics {
                                        contentDescription = cd
                                        role = Role.RadioButton
                                        this.selected = selected
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 24.sp) }
                        }
                    }
                }
            }

            // ---- bio
            SectionLabel(stringResource(R.string.profile_bio))
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= MAX_BIO) bio = it },
                placeholder = { Text(stringResource(R.string.profile_bio_hint), color = TextLow) },
                supportingText = { Text(stringResource(R.string.profile_bio_counter, bio.length, MAX_BIO), color = TextLow) },
                minLines = 2,
                maxLines = 4,
                colors = deadFestFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            // ---- badge colour
            SectionLabel(stringResource(R.string.profile_colour))
            SurfaceCard {
                colours.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        row.forEach { hex ->
                            val selected = hex.equals(color, ignoreCase = true)
                            val cd = stringResource(R.string.cd_colour_option, hex)
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(parseThemeColor(hex))
                                    .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                                    .pressClick(CircleShape) { color = hex }
                                    .semantics {
                                        contentDescription = cd
                                        role = Role.RadioButton
                                        this.selected = selected
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.xxs))
                }
                Text(stringResource(R.string.profile_colour_locked_hint), color = TextLow, style = DeadFestType.body)
            }

            GhostAction(
                text = stringResource(R.string.profile_frames_hint),
                icon = Icons.Default.MilitaryTech,
                onClick = { navController.navigate("progression") },
                modifier = Modifier.fillMaxWidth()
            )

            PrimaryAction(
                text = stringResource(R.string.action_save),
                icon = Icons.Default.Save,
                enabled = changed,
                onClick = {
                    viewModel.updateProfile(avatar, bio, color)
                    navController.popBackStack()
                }
            )
            Spacer(Modifier.height(Spacing.scrollBottom))
        }
    }
}

@Composable
private fun rememberSaveableString(initial: String): MutableState<String> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }
