# Behaviour of bizantine nodes

- **0** - Non-byzantine node
- **1** - Byzantine node is node 1 (that means it is the first leader), and it will propose a block (with unauthorized transactions) in the beggining of the execution. Nodes should reject this.
- **2** - Byzantine node is not the leader, and it tries to change the amount of one of the transactions in the proposed block.
- **3** Byzantine node is not the leader, and it tries to change the amount of the fee (set it in the block) in the proposed block.