# Behaviour of bizantine nodes

- **0** - Non-byzantine node
- **1** - Byzantine leader is node 1 (that means it is the first leader), and it will propose a block (with unauthorized transactions) in the beggining of the execution. Nodes should reject this.