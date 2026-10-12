package com.oflinetok.app.ai

import java.util.Locale

/**
 * Ideas for brand-new tags. CLIP compares each video with "a photo of {english}"; the tag the
 * user sees is in their language (Russian or English). Concrete, visible things work best.
 */
object TagVocabulary {
    class Concept(val english: String, val russian: String) {
        val prompt get() = "a photo of $english"
        fun label(locale: Locale = Locale.getDefault()): String =
            if (locale.language == "ru") russian else english
    }

    val CONCEPTS: List<Concept> = listOf(
        // Animals
        c("cat", "кошка"), c("kitten", "котёнок"), c("dog", "собака"), c("puppy", "щенок"),
        c("bird", "птица"), c("parrot", "попугай"), c("horse", "лошадь"), c("cow", "корова"),
        c("fish", "рыба"), c("aquarium", "аквариум"), c("hamster", "хомяк"), c("rabbit", "кролик"),
        c("snake", "змея"), c("spider", "паук"), c("insect", "насекомое"), c("monkey", "обезьяна"),
        c("bear", "медведь"), c("wild animal", "дикие животные"), c("farm animals", "ферма"),
        // People
        c("baby", "малыш"), c("children", "дети"), c("girl", "девушка"), c("man", "парень"),
        c("old person", "пожилые"), c("couple", "пара"), c("family", "семья"), c("friends", "друзья"),
        c("crowd of people", "толпа"), c("selfie", "селфи"), c("face close-up", "лицо"),
        c("wedding", "свадьба"), c("birthday party", "день рождения"), c("party", "вечеринка"),
        // Activities
        c("people dancing", "танцы"), c("singing", "пение"), c("playing guitar", "гитара"),
        c("playing piano", "пианино"), c("concert", "концерт"), c("cooking", "готовка"),
        c("eating food", "еда"), c("drinking", "напитки"), c("workout in a gym", "спортзал"),
        c("running", "бег"), c("swimming", "плавание"), c("fishing", "рыбалка"),
        c("cycling", "велоспорт"), c("skateboarding", "скейтборд"), c("skiing", "лыжи"),
        c("snowboarding", "сноуборд"), c("surfing", "сёрфинг"), c("climbing", "скалолазание"),
        c("martial arts fight", "единоборства"), c("boxing", "бокс"), c("soccer", "футбол"),
        c("basketball", "баскетбол"), c("hockey", "хоккей"), c("tennis", "теннис"),
        c("volleyball", "волейбол"), c("driving a car", "вождение"), c("car crash", "авария"),
        c("fail", "фейл"), c("prank", "пранк"), c("magic trick", "фокус"), c("makeup", "макияж"),
        c("haircut", "стрижка"), c("fashion outfit", "мода"), c("shopping", "шопинг"),
        c("working on a computer", "работа"), c("studying", "учёба"), c("drawing art", "рисование"),
        c("building and repair", "ремонт"), c("gardening", "сад"), c("camping", "кемпинг"),
        c("travel", "путешествия"), c("fireworks", "фейерверк"), c("fire", "огонь"),
        c("explosion", "взрыв"),
        // Places and nature
        c("beach", "пляж"), c("sea", "море"), c("lake", "озеро"), c("river", "река"),
        c("waterfall", "водопад"), c("mountains", "горы"), c("forest", "лес"), c("field", "поле"),
        c("desert", "пустыня"), c("snow", "снег"), c("winter", "зима"), c("rain", "дождь"),
        c("storm", "гроза"), c("sunset", "закат"), c("sky and clouds", "небо"), c("night sky", "ночное небо"),
        c("city", "город"), c("night city", "ночной город"), c("street", "улица"),
        c("building architecture", "архитектура"), c("bridge", "мост"), c("park", "парк"),
        c("village", "деревня"), c("house", "дом"), c("room interior", "комната"),
        c("kitchen", "кухня"), c("bathroom", "ванная"), c("office", "офис"), c("school classroom", "школа"),
        c("shop", "магазин"), c("restaurant", "ресторан"), c("stadium", "стадион"),
        c("church", "храм"), c("airport", "аэропорт"), c("subway", "метро"), c("road", "дорога"),
        c("garden flowers", "цветы"), c("trees", "деревья"), c("space and planets", "космос"),
        c("underwater", "под водой"),
        // Vehicles and things
        c("car", "машина"), c("sports car", "спорткар"), c("motorcycle", "мотоцикл"),
        c("truck", "грузовик"), c("bus", "автобус"), c("train", "поезд"), c("airplane", "самолёт"),
        c("helicopter", "вертолёт"), c("boat", "лодка"), c("ship", "корабль"), c("bicycle", "велосипед"),
        c("tractor", "трактор"), c("tank", "танк"), c("weapon", "оружие"), c("phone", "телефон"),
        c("computer", "компьютер"), c("tools", "инструменты"), c("money", "деньги"),
        c("toys", "игрушки"), c("books", "книги"), c("clothes", "одежда"), c("shoes", "обувь"),
        // Food
        c("pizza", "пицца"), c("burger", "бургер"), c("cake", "торт"), c("dessert", "десерт"),
        c("fruit", "фрукты"), c("vegetables", "овощи"), c("meat", "мясо"), c("sushi", "суши"),
        c("coffee", "кофе"), c("barbecue", "шашлык"),
        // Kinds of video
        c("meme with text", "мем"), c("screenshot of a phone screen", "скриншот"),
        c("text on screen", "текст"), c("cartoon", "мультфильм"), c("anime", "аниме"),
        c("video game", "игры"), c("minecraft", "майнкрафт"), c("movie scene", "кино"),
        c("music video", "клип"), c("news broadcast", "новости"), c("interview", "интервью"),
        c("tutorial", "обучение"), c("dark video", "темнота"), c("black and white video", "чёрно-белое"),
        c("drone aerial view", "с дрона"), c("time lapse", "таймлапс"), c("slow motion", "замедление"),
        c("video call", "видеозвонок"), c("military", "военное"), c("police", "полиция"),
        c("hospital", "больница"), c("christmas", "новый год"), c("halloween", "хэллоуин"),
    )

    private fun c(english: String, russian: String) = Concept(english, russian)

    /** Concept whose label (in either language) is this tag, so its English prompt can be used. */
    fun forTag(tag: String): Concept? = CONCEPTS.firstOrNull { it.english == tag || it.russian == tag }
}
