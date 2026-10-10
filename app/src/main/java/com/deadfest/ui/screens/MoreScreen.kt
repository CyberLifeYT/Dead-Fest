package com.deadfest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deadfest.ui.components.IconTile
import com.deadfest.ui.components.ScreenHeader
import com.deadfest.ui.components.pressClick
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.MainViewModel

@Composable
fun MoreScreen(navController: NavController, viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "UTILITY HUB", subtitle = "ADVANCED TERMINAL SYSTEMS")

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HubButton("IMAGES", "Photos shared by every survivor", Icons.Default.PhotoLibrary) { navController.navigate("images") } }
            item { HubButton("SCRATCH CARDS", "Instant-win lottery", Icons.Default.ConfirmationNumber) { navController.navigate("scratch") } }
            item { HubButton("PROGRESSION", "Levels, rewards & prestige", Icons.Default.MilitaryTech) { navController.navigate("progression") } }
            item { HubButton("ARCHIVES", "Season history & hall of fame", Icons.Default.History) { navController.navigate("archives") } }
            item { HubButton("SURVIVAL INTEL", "System mechanics & lore", Icons.Default.Info) { navController.navigate("survival_intel") } }
        }
    }
}

@Composable
fun HubButton(label: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pressClick(RoundedCornerShape(22.dp), onClick = onClick),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconTile(icon, size = 48)
            Column {
                Text(label, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(description, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
