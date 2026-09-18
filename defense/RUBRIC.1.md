# Rubric 1 Defense

Each part of my defense will include a snippet from the first rubric, as 
well as an explanation as to why my project meets those specifications.

### Category A -- Build and Structure
| Step | Criterion |
| - | - |
| A1 | Compiles cleanly, machine core in its own file, no external dependencies. |

### Category B -- Configuration Parsing and Precedence
| Step | Criterion |
| - | - |
| B1 | All switches parse; config file parses (# comments, whitespace-separated key/value, last-duplicate-wins); precedence is correct; -mem accepts no-suffix/K/M/G. |

### Category C -- Machine Memory
| Step | Criterion |
| - | - |
| C1 | Allocates exactly mem bytes; address N indexes byte N; not pre-populated. |

### Category D -- Register File and x0 Semantics
| Step | Criterion |
| - | - |
| D1 | 32 registers behind a read/write API; reads of x0 return 0; writes to x0 are discarded; enforced inside the API. |

### Category E -- PC and CSRs
| Step | Criterion |
| - | - |
| E1 | PC present and non-addressable; all CSRs present with correct numbers and read/write behavior (mhartid read-only, satp hardwired to 0 when -mmu off). |

### Category F -- Sign Extension
| Step | Criterion |
| - | - |
| F1 | Correct for all sign-bit positions; contains no loops and no if/else/ternary/switch. |

### Summary

To my knowledge, I have fulfilled all requirements to the best of my 
ability.