import os

file_path = r'c:\Users\AKubyshenko\AndroidStudioProjects\SumUp\app\src\main\java\com\andrewwin\sumup\ui\screens\sources\SourcesScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Fix doubled import and val
text = text.replace("val suggestions by viewModel.suggestions.collectAsState()\n    val suggestions by viewModel.suggestions.collectAsState()", "val suggestions by viewModel.suggestions.collectAsState()")

# Handle insertion at the end of the LazyColumn
import re
text = re.sub(
    r'(\s+onDeleteSource = \{ viewModel\.deleteSource\(it\) \}\s+\)\s+\}\s+)(\}\s+if \(showAddGroupDialog\))',
    r'\1    item {\n                SuggestionsSection(\n                    suggestions = suggestions,\n                    onSubscribe = { viewModel.subscribeToTheme(it) }\n                )\n            }\n        \2',
    text
)

# Append SuggestionSection composable
section = """
@Composable
fun SuggestionsSection(
    suggestions: SuggestionState,
    onSubscribe: (SuggestedTheme) -> Unit
) {
    if (suggestions !is SuggestionState.Success) return

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        if (suggestions.isModelLoaded) {
            Text(
                text = "На основі ваших джерел пропонуємо підписатися на:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            Text(
                text = "Підписуйтесь на теми (для персоналізації завантажте ШІ-модель):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (suggestions.recommendations.isEmpty()) {
            Text(
                text = "Немає нових рекомендацій",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            suggestions.recommendations.forEach { theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = theme.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onSubscribe(theme) }) {
                        Text("Підписатись")
                    }
                }
            }
        }
    }
}
"""

if 'fun SuggestionsSection' not in text:
    text += section

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)
