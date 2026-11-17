# Consignes réutilisables

Une consigne réutilisable est un fichier Markdown de ce dossier, `<nom>.md` (minuscules, chiffres
et tirets). Une consigne d'opération (`prompts/<operation>.txt`) l'inclut en déclarant, seule sur
sa ligne :

```
[skill:<nom>]
```

Le contenu prend la place de la déclaration dans le message système (`SkillLibrary`), jamais dans
le message utilisateur. Une consigne réutilisable déclarée mais absente arrête le démarrage.

Mêmes règles que les consignes d'opération : aucune accolade, des données toujours désignées par
leurs balises. Modifier une consigne change l'empreinte de l'évaluation : relancer l'évaluation
réelle (`./mvnw -pl backend verify -Peval`).
