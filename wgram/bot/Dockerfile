FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY bot.py /app/bot.py
ENV PORT=3000 WGRAM_DATA_DIR=/app/data
RUN mkdir -p /app/data
EXPOSE 3000
CMD ["python", "-u", "bot.py"]
