import openai
import json


def askGPT(prompt_json, message, key):
    openai.api_key = key
    try:
        prompt = json.loads(prompt_json)
        if message:
                prompt.append({"role": "user", "content": message},
                              )
                chat = openai.ChatCompletion.create(
                    model="gpt-3.5-turbo",
                    messages=prompt
                )

                reply = chat.choices[0].message.content

                return reply

    except Exception as e:
        return f'Ошибка: {e}'
