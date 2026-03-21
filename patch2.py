import os

file_path = r'c:\Users\AKubyshenko\AndroidStudioProjects\SumUp\app\src\main\java\com\andrewwin\sumup\ui\screens\sources\SourcesScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Replace padding and bold styling
text = text.replace(
    '''    Column(modifier = Modifier.padding(vertical = 16.dp)) {
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
        }''',
    '''    Column(modifier = Modifier.padding(top = 32.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)) {
        if (suggestions.isModelLoaded) {
            Text(
                text = "На основі ваших джерел пропонуємо підписатися на:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            Text(
                text = "Підписуйтесь на теми (для персоналізації завантажте ШІ-модель):",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }'''
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)

file_path_2 = r'c:\Users\AKubyshenko\AndroidStudioProjects\SumUp\app\src\main\java\com\andrewwin\sumup\domain\usecase\sources\SuggestThemesUseCase.kt'
with open(file_path_2, 'r', encoding='utf-8') as f:
    text2 = f.read()

# Add logs
import_log = 'import android.util.Log\n'
if 'import android.util.Log' not in text2:
    text2 = text2.replace('import javax.inject.Inject', import_log + 'import javax.inject.Inject')

text2 = text2.replace('        val themeScores = mutableMapOf<SuggestedTheme, Int>()', 
'''        Log.d("SuggestThemes", "Starting to calculate recommendations based on ${allArticles.size} articles")
        val themeScores = mutableMapOf<SuggestedTheme, Int>()''')

text2 = text2.replace('''                val similarity = cosineSimilarity(articleEmbedding, themeEmbedding)
                if (similarity > 0.5f) {
                    themeScores[theme] = (themeScores[theme] ?: 0) + 1
                }''',
'''                val similarity = cosineSimilarity(articleEmbedding, themeEmbedding)
                Log.d("SuggestThemes", "Article '${article.title.take(30)}' vs Theme '${theme.name}' -> Similarity: $similarity")
                if (similarity > 0.5f) {
                    themeScores[theme] = (themeScores[theme] ?: 0) + 1
                    Log.d("SuggestThemes", " ---> Matched! Theme '${theme.name}' points: ${themeScores[theme]}")
                }''')

text2 = text2.replace('''        val recommended = themeScores.filter { it.value > thresholdCount }.keys.toList()''',
'''        Log.d("SuggestThemes", "Total articles: $totalArticles, Threshold points: $thresholdCount")
        themeScores.forEach { (theme, points) ->
            Log.d("SuggestThemes", "Final score for '${theme.name}': $points points")
        }
        val recommended = themeScores.filter { it.value > thresholdCount }.keys.toList()
        Log.d("SuggestThemes", "Recommended themes: ${recommended.map { it.name }}")''')

with open(file_path_2, 'w', encoding='utf-8') as f:
    f.write(text2)
