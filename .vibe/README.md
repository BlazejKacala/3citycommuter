# .vibe/ Directory

This directory contains Vibe-specific configuration and skills for the 3citycommuter project.

## Structure

```
.vibe/
├── README.md                    # This file
└── skills/
    └── 3citycommuter/           # Project-specific skill
        ├── skill.md             # Main skill instructions and workflows
        ├── api_reference.md     # Comprehensive ZTM Gdańsk API documentation
        ├── MEMORY.md            # Project memory index
        └── manifest.yaml        # Skill metadata
```

## Skills

### 3citycommuter Skill

The main skill for this project, containing:
- **Project Overview**: Architecture, targets, tech stack
- **Essential Commands**: Building, testing, code quality
- **Architecture Documentation**: Module structure, DI, UI patterns
- **Development Workflows**: Adding screens, repositories, API endpoints
- **API Reference**: Complete ZTM Gdańsk API documentation (26 endpoints)
- **Safety Notes**: Important constraints and warnings

**Usage:** The skill is automatically loaded when working on this project. You can also explicitly load it with:
```
load skill 3citycommuter
```

## Migration from .claude/

This folder contains analogous skills and configuration migrated from the `.claude/` directory:

| .claude/ | .vibe/ | Purpose |
|----------|--------|---------|
| `memory/api_reference.md` | `skills/3citycommuter/api_reference.md` | API endpoint documentation |
| `memory/MEMORY.md` | `skills/3citycommuter/MEMORY.md` | Memory index |
| `CLAUDE.md` | `skills/3citycommuter/skill.md` | Project guidance and workflows |

The `settings.local.json` file from `.claude/` is Claude-specific and has no direct equivalent in Vibe.

## Vibe Configuration

Vibe uses the following configuration locations:
- `~/.vibe/` - Global Vibe configuration (user-level)
- `.vibe/` - Project-local configuration and skills (this directory)

This project's `.vibe/` directory contains only project-specific skills and does not override any global settings.
