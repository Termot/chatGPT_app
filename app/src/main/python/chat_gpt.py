import openai
import json


def askGPT(prompt_json, message, key):
    openai.api_key = key

    try:
        prompt = json.loads(prompt_json)
        if message:
                prompt.append({"role": "user", "content": message},
                              )

                # temperature: параметр, контролирующий степень случайности генерируемого текста.
                # Более высокое значение, например 0,8, делает вывод более случайным,
                # в то время как более низкое значение, например 0,2,
                # делает вывод более детерминированным
                # max_tokens: ограничение на максимальное количество токенов в генерируемом ответе.
                # Это полезно, чтобы ограничить длину ответа и управлять структурой диалога
                # n: количество альтернативных ответов, которые вы хотите получить от модели.
                # OpenAI API может возвращать несколько вариантов ответов,
                # и вы можете выбрать наиболее подходящий
                chat = openai.ChatCompletion.create(
                    model="gpt-3.5-turbo",
                    messages=prompt,
                    temperature=0.6,
                    max_tokens=100,
                    n=1
                )

                reply = chat.choices[0].message.content

                return reply

    except Exception as e:
        return f'Ошибка: {e}'
