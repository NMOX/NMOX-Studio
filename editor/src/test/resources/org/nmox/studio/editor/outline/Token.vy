# pragma version ~=0.4.0
"""
@title Meridian Token
@notice A minimal ERC20 in modern Vyper, the fixture the outline
        tests read. The docstring deliberately mentions
def notAFunction(): and event NotAnEvent: at column zero,
because text inside a docstring is prose, never structure.
"""

from ethereum.ercs import IERC20

implements: IERC20

event Transfer:
    sender: indexed(address)
    receiver: indexed(address)
    value: uint256

event Approval:
    owner: indexed(address)
    spender: indexed(address)
    value: uint256

struct Checkpoint:
    fromBlock: uint256
    balance: uint256

flag Role:
    MINTER
    PAUSER

interface Hook:
    def onTransfer(sender: address, amount: uint256): nonpayable
    def paused() -> bool: view

MAX_SUPPLY: constant(uint256) = 10**27

name: public(String[32])
symbol: public(String[8])
decimals: public(uint8)
totalSupply: public(uint256)
balanceOf: public(HashMap[address, uint256])
allowance: public(HashMap[address, HashMap[address, uint256]])
roles: HashMap[address, Role]
owner: immutable(address)


@deploy
def __init__(_name: String[32], _symbol: String[8], _supply: uint256):
    self.name = _name
    self.symbol = _symbol
    self.decimals = 18
    owner = msg.sender
    self.roles[msg.sender] = Role.MINTER
    self._mint(msg.sender, _supply)


@internal
def _mint(receiver: address, amount: uint256):
    # def commentedOut(): stays a comment
    assert self.totalSupply + amount <= MAX_SUPPLY, "cap exceeded"
    self.totalSupply += amount
    self.balanceOf[receiver] += amount
    log Transfer(sender=empty(address), receiver=receiver, value=amount)


@external
def transfer(receiver: address, amount: uint256) -> bool:
    self.balanceOf[msg.sender] -= amount
    self.balanceOf[receiver] += amount
    log Transfer(sender=msg.sender, receiver=receiver, value=amount)
    return True


@external
def transferFrom(sender: address, receiver: address, amount: uint256) -> bool:
    self.allowance[sender][msg.sender] -= amount
    self.balanceOf[sender] -= amount
    self.balanceOf[receiver] += amount
    log Transfer(sender=sender, receiver=receiver, value=amount)
    return True


@external
def approve(spender: address, amount: uint256) -> bool:
    self.allowance[msg.sender][spender] = amount
    log Approval(owner=msg.sender, spender=spender, value=amount)
    return True


@external
@nonreentrant
def mint(receiver: address, amount: uint256):
    assert Role.MINTER in self.roles[msg.sender], "not a minter"
    self._mint(receiver, amount)


@external
@view
def owner_of_contract() -> address:
    return owner
