# MiniYACC Tool

SLR(1) parser generator and grammar analyzer with a browser interface, written in Java 17.
Compiler Design mini-project.

## Features
- Plain-text grammar input (one production per line, `eps` for epsilon)
- FIRST and FOLLOW sets
- LR(0) item sets and transitions
- SLR(1) ACTION / GOTO table with an SLR conflict flag
- Shift-reduce parsing with a step-by-step trace (accept / reject)
- Generates a standalone Java parser into `generated/`
- Plain Java HTTP server (`com.sun.net.httpserver`), Docker-ready

## Grammar format
```
E -> E + T | T
T -> T * F | F
F -> ( E ) | id
```
Symbols are separated by spaces. Symbols on a left-hand side are non-terminals; all others are terminals.

## Run locally
```
javac -encoding UTF-8 -d out src/*.java
java -cp out Server
```
Open http://localhost:8080

## Run with Docker
```
docker build -t miniyacc-tool .
docker run -p 8080:8080 miniyacc-tool
```

## API
| Endpoint | Method | Form fields | Purpose |
|---|---|---|---|
| `/api/health` | GET | none | server status |
| `/api/analyze` | POST | rules, start | FIRST/FOLLOW, item sets, SLR table |
| `/api/parse` | POST | rules, start, input | analysis plus shift-reduce trace |
| `/api/generate` | POST | rules, start | writes `generated/GeneratedParser.java` |

## Project structure
```
src/        Java source (SlrEngine, ParserGenerator, Server)
web/        Browser interface (HTML, CSS, JavaScript)
generated/  Output folder for generated parsers
Dockerfile  Build and run image (Eclipse Temurin 17)
```

## Limitations
SLR(1) only (not LALR/LR(1)); syntax analysis only: no lexer, no semantic actions.
