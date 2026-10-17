package com.vignan.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ProfileSection(val label: String) {
    ACADEMIC_BIO("ACADEMIC & BIO"),
    FEES_RECORD("FEES & RECORD")
}

@Composable
fun ProfileScreen(
    liveResponse: LiveAttendanceResponse?,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf(ProfileSection.ACADEMIC_BIO) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1800)
            isCopied = false
        }
    }

    val profile = liveResponse?.profile
    val personal = profile?.personal
    val parents = profile?.parents
    val education = profile?.education?.filter {
        // Only display qualifications that have genuine recorded data
        it.qualification.isNotBlank() && (it.htNo.isNotBlank() || it.institute.isNotBlank() || it.obtainedMarks.isNotBlank())
    } ?: emptyList()
    val fees = profile?.fees
    val status = profile?.status

    // Trigger an auto-refresh if the user navigates here with only stale/partial cached data
    LaunchedEffect(personal) {
        if (personal == null && !isRefreshing) {
            onRefresh()
        }
    }

    val name = personal?.name?.takeIf { it.isNotBlank() } ?: profile?.name ?: "STUDENT"
    val rollNo = personal?.rollNo?.takeIf { it.isNotBlank() } ?: profile?.rollNo ?: "—"
    val admissionNo = personal?.admissionNo?.takeIf { it.isNotBlank() }
    val branch = personal?.branch?.takeIf { it.isNotBlank() } ?: profile?.branch ?: "—"
    val semester = personal?.semester?.takeIf { it.isNotBlank() } ?: profile?.semester ?: "—"
    val course = personal?.course?.takeIf { it.isNotBlank() } ?: "B.Tech"

    val agg = profile?.aggregate
    val held = agg?.held ?: 0
    val attended = agg?.attended ?: 0
    val pct = agg?.percentage ?: 0.0

    val initials = remember(name) {
        val parts = name.split(" ").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
            parts.isNotEmpty() && parts[0].length >= 2 -> parts[0].take(2).uppercase()
            parts.isNotEmpty() -> parts[0].take(1).uppercase()
            else -> "ST"
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            eyebrow = "PROFILE",
            title = "Student account",
            // The card below owns the identity (name, roll, branch); the header
            // carries the one thing it can't — how fresh this snapshot is.
            subtitle = liveResponse?.scrapedAt
                ?.takeIf { it.isNotBlank() }
                ?.let { "Last sync  ${formatFetchedAt(it)}" }
                ?: "Awaiting first sync",
            onSettingsClick = onOpenSettings
        )

        // ================= HERO: Sleek Student Identity Card =================
        // Lifted one step off the page surface (SurfaceCard + lighter hairline)
        // with the key metrics set into their own inset strip, so the card reads
        // as the page's anchor instead of another flat panel.
        val attendanceAccent = attendanceStatusColor(hasData = held > 0, overallPercentage = pct)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TrackerColors.SurfaceCard)
                .border(1.dp, TrackerColors.HairlineBorderLight, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Identity Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Monogram, ringed in the attendance verdict colour
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TrackerColors.SurfaceElevated)
                        .border(1.5.dp, attendanceAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = TrackerColors.PrimaryWhite,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = TrackerColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rollNo,
                            color = TrackerColors.TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Copy roll no — icon-only button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (rollNo.isNotBlank() && rollNo != "—") {
                                        clipboardManager.setText(AnnotatedString(rollNo))
                                        isCopied = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            CopyIcon(
                                copied = isCopied,
                                tint = if (isCopied) TrackerColors.SafeEmerald else TrackerColors.TextMuted
                            )
                        }

                        if (!admissionNo.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADM $admissionNo",
                                color = TrackerColors.TextSubtle,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Course & Branch Tagline
            Text(
                text = "$course  •  $branch",
                color = TrackerColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (semester.isNotBlank() && semester != "—") {
                    StatusTag(text = semester, color = TrackerColors.PrimaryAccent)
                }

                val rank = personal?.rank?.takeIf { it.isNotBlank() }
                val entrance = personal?.entranceType?.takeIf { it.isNotBlank() } ?: "ENTRANCE"
                if (rank != null) {
                    StatusTag(text = "$entrance #$rank", color = TrackerColors.SafeEmerald)
                }

                val seatType = personal?.seatType?.takeIf { it.isNotBlank() }
                if (seatType != null) {
                    StatusTag(text = seatType, color = TrackerColors.TextMuted)
                }
            }

            // Quick Metric Strip — inset panel, so the three stats read as a
            // single grouped block instead of three loose columns.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TrackerColors.SurfaceElevated)
                    .border(1.dp, TrackerColors.HairlineBorderLight, RoundedCornerShape(12.dp))
                    .padding(vertical = 13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroMetricTile(
                        label = "ATTENDANCE",
                        value = if (held > 0) "${formatPercentage(pct)}" else "—",
                        subtext = if (held > 0) "$attended/$held classes" else "Register active",
                        accentColor = attendanceAccent,
                        modifier = Modifier.weight(1f)
                    )

                    MetricDivider()

                    val backlogCount = status?.backlogs?.let {
                        if (it.contains("no backlogs", ignoreCase = true)) "0" else it
                    } ?: "0"

                    HeroMetricTile(
                        label = "BACKLOGS",
                        value = backlogCount,
                        subtext = if (backlogCount == "0") "Clear record" else "Pending",
                        accentColor = if (backlogCount == "0") TrackerColors.SafeEmerald else TrackerColors.DangerRose,
                        modifier = Modifier.weight(1f)
                    )

                    MetricDivider()

                    val dueText = fees?.totalDue?.takeIf { it.isNotBlank() } ?: "00.00"
                    val isDue = dueText != "00.00" && dueText != "0.00" && dueText != "0"

                    HeroMetricTile(
                        label = "FEE DUES",
                        value = if (isDue) "₹$dueText" else "₹0",
                        subtext = if (isDue) "Pending" else "Settled",
                        accentColor = if (isDue) TrackerColors.WarningAmber else TrackerColors.SafeEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ================= SECTION SELECTOR TABS =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ProfileSection.entries.forEach { section ->
                val isSelected = section == activeSection
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) TrackerColors.SurfaceElevated else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) TrackerColors.HairlineBorderLight else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { activeSection = section }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = section.label,
                        color = if (isSelected) TrackerColors.PrimaryWhite else TrackerColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // ================= TAB CONTENT =================
        when (activeSection) {
            ProfileSection.ACADEMIC_BIO -> {
                AcademicAndBioSection(
                    personal = personal,
                    parents = parents,
                    education = education,
                    isLoading = isRefreshing && personal == null
                )
            }

            ProfileSection.FEES_RECORD -> {
                FeesAndConductSection(
                    fees = fees,
                    status = status,
                    scrapedAt = liveResponse?.scrapedAt,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onLogout = onLogout
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ============================================================================
// 1. ACADEMIC & BIO TAB CONTENT
// ============================================================================

@Composable
private fun AcademicAndBioSection(
    personal: PersonalDetails?,
    parents: ParentDetails?,
    education: List<EducationRecord>,
    isLoading: Boolean
) {
    if (isLoading) {
        LoadingPlaceholderCard(message = "Synchronizing student ERP records...")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // --- 1. Academic & Admission Record ---
        SectionCard(title = "ADMISSION & ENROLLMENT") {
            RowField("Admission Number", personal?.admissionNo)
            RowField("University Roll No", personal?.rollNo)
            RowField("Program / Degree", personal?.course)
            RowField("Specialization / Branch", personal?.branch)
            RowField("Current Semester", personal?.semester)
            RowField("Seat Category", personal?.seatType)
            RowField("Caste / Reservation", personal?.categoryCaste)
            RowField("Entrance Exam & Rank", listOfNotNull(personal?.entranceType, personal?.rank?.let { "#$it" }).joinToString(" — ").ifBlank { null })
            RowField("Date of Admission", personal?.joiningDate)
            RowField("Scholarship Availed", personal?.scholarship)
        }

        // --- 2. Personal & Contact Profile ---
        SectionCard(title = "PERSONAL DETAILS") {
            RowField("Full Name", personal?.name)
            RowField("Gender", personal?.gender)
            RowField("Date of Birth", personal?.dob)
            RowField("Nationality", personal?.nationality)
            RowField("Religion", personal?.religion)
            RowField("Primary Mobile", personal?.mobileNo?.takeIf { it.isNotBlank() }?.let { "+91 $it" })
            if (!personal?.phoneNo.isNullOrBlank()) {
                RowField("Secondary Phone", personal.phoneNo)
            }
            if (!personal?.email.isNullOrBlank()) {
                RowField("Email Address", personal.email)
            }
            if (!personal?.aadharNo.isNullOrBlank()) {
                RowField("Aadhaar Number", personal.aadharNo)
            }
        }

        // --- 3. Parent & Family Details ---
        SectionCard(title = "FAMILY & PARENT DETAILS") {
            RowField("Father's Name", parents?.fatherName)
            RowField("Father's Occupation", parents?.fatherOccupation)
            RowField("Father's Mobile", parents?.fatherMobile?.takeIf { it.isNotBlank() }?.let { "+91 $it" })
            RowField("Mother's Name", parents?.motherName)
            if (!parents?.motherOccupation.isNullOrBlank()) {
                RowField("Mother's Occupation", parents.motherOccupation)
            }
            if (!parents?.motherMobile.isNullOrBlank()) {
                RowField("Mother's Mobile", "+91 ${parents.motherMobile}")
            }
            RowField("Annual Family Income", parents?.annualIncome?.takeIf { it.isNotBlank() }?.let { "₹$it" })
        }

        // --- 4. Residential Address ---
        if (!parents?.correspondenceAddress.isNullOrBlank() || !parents?.permanentAddress.isNullOrBlank()) {
            val addr = parents.correspondenceAddress.ifBlank { parents.permanentAddress }
            SectionCard(title = "RESIDENTIAL ADDRESS") {
                val lines = addr.split("\n", "\r").map { it.trim() }.filter { it.isNotBlank() }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            color = TrackerColors.TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TrackerColors.SafeEmerald)
                    )
                    Text(
                        text = "Official correspondence and permanent residence",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        // --- 5. Prior Educational Qualifications ---
        if (education.isNotEmpty()) {
            SectionCard(title = "PRIOR EDUCATION QUALIFICATIONS") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    education.forEachIndexed { idx, edu ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TrackerColors.SurfaceElevated)
                                .border(1.dp, TrackerColors.HairlineBorderLight, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = edu.qualification,
                                        color = TrackerColors.TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )

                                    if (edu.yearOfPass.isNotBlank()) {
                                        Text(
                                            text = "Class of ${edu.yearOfPass}",
                                            color = TrackerColors.PrimaryAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (edu.institute.isNotBlank()) {
                                    Text(
                                        text = edu.institute,
                                        color = TrackerColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }

                                if (edu.board.isNotBlank()) {
                                    Text(
                                        text = edu.board,
                                        color = TrackerColors.TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (edu.htNo.isNotBlank()) {
                                        Text(
                                            text = "HT: ${edu.htNo}",
                                            color = TrackerColors.TextMuted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    if (edu.obtainedMarks.isNotBlank() && edu.maxMarks.isNotBlank()) {
                                        val marksObtained = edu.obtainedMarks.toDoubleOrNull()
                                        val maxMarks = edu.maxMarks.toDoubleOrNull()
                                        val scorePct = if (marksObtained != null && maxMarks != null && maxMarks > 0) {
                                            " (${formatPercentage((marksObtained / maxMarks) * 100)})"
                                        } else ""

                                        Text(
                                            text = "${edu.obtainedMarks} / ${edu.maxMarks}$scorePct",
                                            color = TrackerColors.SafeEmerald,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. FEES & RECORD TAB CONTENT
// ============================================================================

@Composable
private fun FeesAndConductSection(
    fees: FeeDetails?,
    status: ProfileStatus?,
    scrapedAt: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    var showSignOutConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // --- 1. Fee Balance Summary Banner ---
        val totalDue = fees?.totalDue?.takeIf { it.isNotBlank() } ?: "00.00"
        val totalPayable = fees?.totalPayable?.takeIf { it.isNotBlank() } ?: "00.00"
        val totalPaid = fees?.totalPaid?.takeIf { it.isNotBlank() } ?: "00.00"
        val isDue = totalDue != "00.00" && totalDue != "0.00" && totalDue != "0"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOTAL BALANCE OUTSTANDING",
                        color = TrackerColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDue) TrackerColors.WarningAmberSubtle else TrackerColors.SafeEmeraldSubtle)
                            .border(
                                1.dp,
                                if (isDue) TrackerColors.WarningAmber.copy(alpha = 0.5f) else TrackerColors.SafeEmerald.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isDue) "DUES PENDING" else "CLEARED",
                            color = if (isDue) TrackerColors.WarningAmber else TrackerColors.SafeEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = "₹$totalDue",
                    color = TrackerColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )

                if (!fees?.balanceText.isNullOrBlank()) {
                    Text(
                        text = "Amount: ${fees.balanceText}",
                        color = TrackerColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TrackerColors.HairlineBorder)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TOTAL PAYABLE",
                            color = TrackerColors.TextSubtle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "₹$totalPayable",
                            color = TrackerColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TOTAL PAID",
                            color = TrackerColors.TextSubtle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "₹$totalPaid",
                            color = TrackerColors.SafeEmerald,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- 2. Itemized Ledger Breakdown ---
        SectionCard(title = "COLLEGE FEE BREAKDOWN") {
            if (fees != null && fees.items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fees.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.sNo}.",
                                    color = TrackerColors.TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    text = item.feeName,
                                    color = TrackerColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "₹${item.feeAmount}",
                                color = TrackerColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No fee breakdown items found on portal.",
                    color = TrackerColors.TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // --- 3. Institutional Record & Discipline ---
        SectionCard(title = "INSTITUTIONAL RECORD & CONDUCT") {
            val backlogs = status?.backlogs ?: "Student have no backlogs"
            val outings = status?.outings ?: "No outings"
            val counseling = status?.counseling ?: "No counseling data !"
            val disciplinary = status?.disciplinary ?: "No complaints !"

            StatusRow(
                label = "Academic Standing",
                value = backlogs,
                isClear = backlogs.contains("no backlogs", ignoreCase = true)
            )

            StatusRow(
                label = "Disciplinary Record",
                value = disciplinary,
                isClear = disciplinary.contains("no complaints", ignoreCase = true)
            )

            StatusRow(
                label = "Campus Outing Approvals",
                value = outings,
                isClear = true
            )

            StatusRow(
                label = "Faculty Counseling",
                value = counseling,
                isClear = true
            )
        }

        // --- 4. Account Actions ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
        ) {
            // Sync row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isRefreshing) { onRefresh() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = TrackerColors.TextMuted,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = if (isRefreshing) "Syncing…" else "Sync from ERP",
                        color = TrackerColors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Text(
                    text = scrapedAt?.take(16)?.replace("T", "  ") ?: "—",
                    color = TrackerColors.TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TrackerColors.HairlineBorder)
            )

            // Sign out row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSignOutConfirm = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sign out",
                    color = TrackerColors.DangerRose,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }

    if (showSignOutConfirm) {
        SignOutConfirmDialog(
            onDismiss = { showSignOutConfirm = false },
            onConfirm = {
                showSignOutConfirm = false
                onLogout()
            }
        )
    }
}

// ============================================================================
// SIGN OUT CONFIRMATION
// ============================================================================

/**
 * Sign-out confirmation, built like an iOS alert: lifted card, a glyph badge,
 * a centred title + message, then a horizontal button row split by hairlines —
 * the destructive action in red, cancel in the accent colour. No filled pills.
 */
@Composable
internal fun SignOutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(22.dp))
                .background(TrackerColors.SurfaceElevated)
                .border(1.dp, TrackerColors.HairlineBorderLight, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TrackerColors.DangerRoseSubtle)
                        .border(1.dp, TrackerColors.DangerRose.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SignOutGlyph(color = TrackerColors.DangerRose)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Sign out?",
                    color = TrackerColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "You'll need your registration number and password to sign back in.",
                    color = TrackerColors.TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TrackerColors.HairlineBorderLight)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                AlertAction(
                    label = "Cancel",
                    color = TrackerColors.TextPrimary,
                    weight = FontWeight.Medium,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(TrackerColors.HairlineBorderLight)
                )

                AlertAction(
                    label = "Sign out",
                    color = TrackerColors.DangerRose,
                    weight = FontWeight.SemiBold,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** One full-bleed button of the alert's action row. */
@Composable
private fun AlertAction(
    label: String,
    color: Color,
    weight: FontWeight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 16.sp,
            fontWeight = weight,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/** Door + outgoing arrow, in the same canvas hairline style as the other glyphs. */
@Composable
private fun SignOutGlyph(
    modifier: Modifier = Modifier,
    color: Color = TrackerColors.DangerRose
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Door: three sides of a rounded box, open on the right.
        val door = Path().apply {
            moveTo(w * 0.60f, h * 0.13f)
            lineTo(w * 0.20f, h * 0.13f)
            lineTo(w * 0.20f, h * 0.87f)
            lineTo(w * 0.60f, h * 0.87f)
        }
        drawPath(door, color, style = stroke)

        // Arrow leaving the door.
        drawLine(color, Offset(w * 0.46f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.70f, h * 0.34f), Offset(w * 0.86f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.70f, h * 0.66f), Offset(w * 0.86f, h * 0.5f), sw, StrokeCap.Round)
    }
}

// ============================================================================
// UI BUILDING BLOCKS & COMPONENTS
// ============================================================================

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                color = TrackerColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.8.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TrackerColors.HairlineBorder)
            )

            content()
        }
    }
}

@Composable
private fun RowField(label: String, value: String?) {
    val displayValue = value?.takeIf { it.isNotBlank() } ?: return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TrackerColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif
        )

        Text(
            text = displayValue,
            color = TrackerColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String, isClear: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TrackerColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isClear) TrackerColors.SafeEmerald else TrackerColors.DangerRose)
            )

            Text(
                text = value,
                color = if (isClear) TrackerColors.TextPrimary else TrackerColors.DangerRose,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HeroMetricTile(
    label: String,
    value: String,
    subtext: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TrackerColors.TextSubtle,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = value,
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtext,
            color = TrackerColors.TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Hairline rule between the hero card's inset metrics. */
@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(38.dp)
            .background(TrackerColors.HairlineBorderLight)
    )
}

/** Minimal copy glyph (two offset rounded sheets); swaps to a check while copied. */
@Composable
private fun CopyIcon(copied: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(15.dp)) {
        if (copied) {
            val path = Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.54f)
                lineTo(size.width * 0.40f, size.height * 0.76f)
                lineTo(size.width * 0.84f, size.height * 0.26f)
            }
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = size.width * 0.13f, cap = StrokeCap.Round)
            )
        } else {
            val stroke = size.width * 0.11f
            val radius = CornerRadius(size.width * 0.18f, size.width * 0.18f)

            // Back sheet
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.34f, size.height * 0.06f),
                size = Size(size.width * 0.56f, size.height * 0.60f),
                cornerRadius = radius,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Front sheet (filled so it occludes the back sheet's lower-left part)
            drawRoundRect(
                color = TrackerColors.SurfaceDark,
                topLeft = Offset(size.width * 0.10f, size.height * 0.30f),
                size = Size(size.width * 0.62f, size.height * 0.64f),
                cornerRadius = radius
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.10f, size.height * 0.30f),
                size = Size(size.width * 0.62f, size.height * 0.64f),
                cornerRadius = radius,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun LoadingPlaceholderCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TrackerColors.SurfaceDark)
            .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(12.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = TrackerColors.TextSecondary,
                strokeWidth = 2.dp
            )
            Text(
                text = message,
                color = TrackerColors.TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
