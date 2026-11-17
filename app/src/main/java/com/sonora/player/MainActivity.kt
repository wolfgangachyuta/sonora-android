package com.sonora.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF171717)
private val Paper = Color(0xFFF5F4F0)
private val Muted = Color(0xFF888781)
private val Accent = Color(0xFFB6D3C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SonoraApp() }
    }
}

@Composable
private fun SonoraApp() {
    var selectedTab by remember { mutableStateOf("Home") }
    var isPlaying by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Paper,
            contentColor = Ink
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("SONORA", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("YOUR MUSIC, YOUR SPACE.", fontSize = 9.sp, color = Muted, letterSpacing = 2.sp)
                    }
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(Ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Paper)
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TUESDAY, 17 NOV", fontSize = 10.sp, color = Muted, letterSpacing = 1.8.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Good evening.", fontSize = 31.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
                    }
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = Muted, modifier = Modifier.padding(bottom = 8.dp).size(26.dp))
                }

                Spacer(Modifier.height(28.dp))
                Text("PICK UP WHERE YOU LEFT OFF", fontSize = 9.sp, color = Muted, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(13.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFE6E5DF)).padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtwork(Modifier.size(94.dp))
                    Spacer(Modifier.width(15.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CONTINUE LISTENING", fontSize = 8.sp, color = Muted, letterSpacing = 1.3.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Your library awaits", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Scan your music to begin", fontSize = 11.sp, color = Muted)
                    }
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(34.dp), tint = Ink)
                }

                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR COLLECTION", fontSize = 9.sp, color = Muted, letterSpacing = 1.8.sp, modifier = Modifier.weight(1f))
                    Text("VIEW ALL  ↗", fontSize = 9.sp, color = Ink, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CollectionCard("Albums", "0", Icons.Rounded.Album, Modifier.weight(1f))
                    CollectionCard("Artists", "0", Icons.Rounded.GraphicEq, Modifier.weight(1f))
                    CollectionCard("Favourites", "0", Icons.Rounded.FavoriteBorder, Modifier.weight(1f))
                }

                Spacer(Modifier.height(29.dp))
                Text("MADE FOR THIS MOMENT", fontSize = 9.sp, color = Muted, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("A little room
for discovery.", color = Paper, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text("Your listening story starts here.", color = Color(0xFFAAA9A2), fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier.size(74.dp).clip(RoundedCornerShape(14.dp)).background(
                            Brush.linearGradient(listOf(Color(0xFFB9D8C8), Color(0xFF657B75), Color(0xFF303B39)))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Ink, modifier = Modifier.size(38.dp))
                    }
                }

                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavItem("Home", Icons.Rounded.Home, selectedTab == "Home") { selectedTab = "Home" }
                    NavItem("Library", Icons.Rounded.LibraryMusic, selectedTab == "Library") { selectedTab = "Library" }
                    NavItem("Search", Icons.Rounded.Search, selectedTab == "Search") { selectedTab = "Search" }
                    NavItem("You", Icons.Rounded.GraphicEq, selectedTab == "You") { selectedTab = "You" }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE6E5DF)).padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(39.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFB9D8C8)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Ink)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nothing playing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Choose something from your library", fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp).clickable { isPlaying = !isPlaying }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AlbumArtwork(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(13.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF243A35), Color(0xFF9AB5A3), Color(0xFFE1CBB0)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun CollectionCard(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFE6E5DF)).padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = Ink)
        Spacer(Modifier.height(17.dp))
        Text(count, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Text(title, fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Ink else Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.sp, color = if (selected) Ink else Muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
