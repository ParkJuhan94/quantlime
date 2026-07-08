FROM python:3.11-slim
WORKDIR /app

# 의존성 레이어 캐시: requirements.txt만 먼저 복사해 소스만 바뀐
# 재빌드에서 pip install을 다시 하지 않도록 한다.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY calculator calculator
COPY main.py schemas.py ./

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
