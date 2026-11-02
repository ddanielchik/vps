-- Добавляем состав коктейля в таблицу меню
ALTER TABLE menu
    ADD COLUMN ingredients VARCHAR(500);


-- дбавили 5 вкусов коктейлкй
INSERT INTO taste_tags (name)
VALUES
    ('SWEET'),
    ('SOUR'),
    ('BITTER'),
    ('SALTY'),
    ('SPICY')
ON CONFLICT (name) DO NOTHING;


-- Добавляем коктейли и сразу связываем их со вкусами
WITH cocktails (name_product, price, ingredients, tags) AS (
    VALUES
        -- Алкогольные коктейли из PDF
        ('Mojito', 490.00,
         'Ром, лимон, коричневый сахар, содовая, мята',
         ARRAY['SWEET', 'SOUR']),

        ('Limoncello Iced Tea', 520.00,
         'Лимончелло, чёрный чай, игристое вино, лимонный соус',
         ARRAY['SWEET', 'SOUR']),

        ('Aperitivo Spritz', 530.00,
         'Игристое вино, аперитиво биттер, содовая, апельсин',
         ARRAY['BITTER', 'SWEET']),

        ('Yellow Sun', 550.00,
         'Ром, водка, мандариновый соус, ананасовый сок, лимонный соус',
         ARRAY['SWEET', 'SOUR']),

        ('Strawberry Frozen Daiquiri', 540.00,
         'Текила, апельсиновый ликёр, лимонный соус, сахарный сироп, клубничное пюре',
         ARRAY['SWEET', 'SOUR']),

        ('Lynchburg Lemonade', 580.00,
         'Виски, кисло-сладкий микс, апельсиновый ликёр, Sprite',
         ARRAY['SWEET', 'SOUR']),

        ('Cucumber Martini', 560.00,
         'Джин, сухой вермут, лимонный соус, сахарный сироп, базилик',
         ARRAY['SOUR', 'SWEET']),

        ('Cream the Banana', 490.00,
         'Сливочный ликёр, банановый ликёр, кофейный сироп, молоко',
         ARRAY['SWEET']),

        ('Mai Tai', 590.00,
         'Ром, миндальный ликёр, апельсиновый сок, лимон, гренадин',
         ARRAY['SWEET', 'SOUR']),

        ('Gin Basil Smash', 560.00,
         'Джин, сахарный сироп, лимон, базилик, содовая',
         ARRAY['SOUR', 'SWEET']),

        ('Violet Snow', 540.00,
         'Джин, фиалковый сироп, лимонный соус, биттер-апельсин',
         ARRAY['SWEET', 'BITTER']),

        ('Hemingway Margarita', 570.00,
         'Белый ром, вишнёвый ликёр, грейпфрутовый сок, лимонный соус',
         ARRAY['SOUR']),

        ('Pumpkin Cane', 530.00,
         'Ром, сахарный сироп, тыквенное пюре, апельсиновый сок, корица',
         ARRAY['SWEET']),

        ('Grass', 550.00,
         'Водка, зелёная слива, щавель, лемонграсс, цитрусовый микс',
         ARRAY['SOUR']),

        ('Quince', 560.00,
         'Джин, айва, шалфей, лайм',
         ARRAY['SOUR']),

        ('Tuxedo', 540.00,
         'Водка, бергамот, ваниль, персик',
         ARRAY['SWEET']),

        ('Passion Martini', 580.00,
         'Водка, маракуйя, ваниль, цитрусовый микс',
         ARRAY['SWEET', 'SOUR']),

        ('White Peach', 540.00,
         'Текила, белый чай, персик',
         ARRAY['SWEET']),

        ('Aperol Spritz', 550.00,
         'Игристое вино, Aperol, содовая',
         ARRAY['BITTER', 'SWEET']),

        ('Strawberry Margarita', 570.00,
         'Текила, лимон, клубника, апельсиновый ликёр',
         ARRAY['SWEET', 'SOUR']),

        -- Дополнительные классические коктейли
        ('Cuba Libre', 450.00,
         'Ром, кола, лайм, лёд',
         ARRAY['SWEET', 'SOUR']),

        ('Cuba Libre Sprite', 450.00,
         'Ром, Sprite, лимонный сок, лёд',
         ARRAY['SWEET', 'SOUR']),

        ('Negroni', 590.00,
         'Джин, красный вермут, Campari',
         ARRAY['BITTER', 'SWEET']),

        ('Whiskey Sour', 570.00,
         'Виски, лимонный сок, сахарный сироп',
         ARRAY['SOUR', 'SWEET']),

        ('Margarita', 550.00,
         'Текила, апельсиновый ликёр, лайм, соль для ободка',
         ARRAY['SOUR', 'SALTY']),

        ('Pina Colada', 530.00,
         'Ром, ананасовый сок, кокосовые сливки',
         ARRAY['SWEET']),

        ('Bloody Mary', 520.00,
         'Водка, томатный сок, лимонный сок, острый соус, соль, специи',
         ARRAY['SALTY', 'SPICY']),

        ('Dirty Martini', 590.00,
         'Джин, сухой вермут, оливковый рассол',
         ARRAY['SALTY'])
),
     inserted AS (
         INSERT INTO menu (name_product, price, ingredients)
             SELECT name_product, price, ingredients
             FROM cocktails
             RETURNING id_position, name_product
     )
INSERT INTO menu_taste_tags (id_position, id_tag)
SELECT inserted.id_position, taste_tags.id_tag
FROM inserted
         JOIN cocktails
              ON inserted.name_product = cocktails.name_product
         JOIN taste_tags
              ON taste_tags.name = ANY(cocktails.tags);