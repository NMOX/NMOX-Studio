# @version ^0.3.10

owner: public(address)
balances: public(HashMap[address, uint256])

event Deposit:
    sender: indexed(address)
    amount: uint256

@external
def __init__():
    self.owner = msg.sender

@external
@payable
def deposit():
    self.balances[msg.sender] += msg.value
    log Deposit(msg.sender, msg.value)

@external
@view
def balance_of(who: address) -> uint256:
    return self.balances[who]
