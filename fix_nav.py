import sys

main_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(main_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Fix 'togetherWith'
text = text.replace('togetherWith', 'with')

# Replace the inline NavigationBar with BottomNavBar
bad_nav = """        // Bottom Nav Bar hook
        androidx.compose.material3.NavigationBar(
            containerColor = FinalWhite,
            modifier = Modifier.align(Alignment.BottomCenter).border(1.dp, FinalMist)
        ) {
            val tabs = listOf("Home", "Progress", "Exercises", "Rewards")
            val icons = listOf(androidx.compose.material.icons.Icons.Default.Home, androidx.compose.material.icons.Icons.Default.DateRange, androidx.compose.material.icons.Icons.Default.FavoriteBorder, androidx.compose.material.icons.Icons.Default.Star)
            val mapping = listOf("Home", "Rewards", "Exercises", "Rewards")
            
            tabs.forEachIndexed { index, title ->
                val targetTab = mapping[index]
                androidx.compose.material3.NavigationBarItem(
                    selected = (currentTab == targetTab),
                    onClick = { currentTab = targetTab },
                    icon = { androidx.compose.material3.Icon(icons[index], contentDescription = title) },
                    label = { androidx.compose.material3.Text(title, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = FinalMoss,
                        selectedTextColor = FinalMoss,
                        unselectedIconColor = FinalMuted,
                        unselectedTextColor = FinalMuted,
                        indicatorColor = FinalSagePale
                    )
                )
            }
        }"""

good_nav = """        // Bottom Nav Bar hook
        BottomNavBar(
            currentTab = currentTab,
            onTabChange = { currentTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )"""

text = text.replace(bad_nav, good_nav)

bottom_nav = """
@Composable
fun BottomNavBar(currentTab: String, onTabChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.NavigationBar(
        modifier = modifier.border(1.dp, FinalMist),
        containerColor = FinalWhite
    ) {
        val tabs = listOf("Home", "Progress", "Exercises", "Rewards")
        val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.FavoriteBorder, Icons.Default.Star)
        val mapping = listOf("Home", "Rewards", "Exercises", "Rewards")

        tabs.forEachIndexed { index, title ->
            androidx.compose.material3.NavigationBarItem(
                selected = (currentTab == mapping[index]),
                onClick = { onTabChange(mapping[index]) },
                icon = { androidx.compose.material3.Icon(icons[index], contentDescription = title) },
                label = { androidx.compose.material3.Text(title, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = FinalMoss,
                    selectedTextColor = FinalMoss,
                    unselectedIconColor = FinalMuted,
                    unselectedTextColor = FinalMuted,
                    indicatorColor = FinalSagePale
                )
            )
        }
    }
}
"""
if 'fun BottomNavBar' not in text:
    text += bottom_nav

with open(main_path, 'w', encoding='utf-8') as f:
    f.write(text)

print("NavigationBar fixed!")
