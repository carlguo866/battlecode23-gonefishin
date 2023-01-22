import math as m

def getOptimal(dis):
    best_resource_per_turn = 0
    best_weight = 0
    for weight in range(10, 41):
        turns = m.ceil(dis / 2) # go to mine
        turns += m.ceil(weight / 0.8) # mine
        cd = m.floor(5 + 3 * weight / 8)
        turns += m.ceil(dis * cd / 10) # go back home
        resource_per_turn = weight / turns
        if resource_per_turn > best_resource_per_turn:
            best_resource_per_turn = resource_per_turn
            best_weight = weight
    print(f"dis {dis:02d} best weight {best_weight:02d}")

for dis in range(4, 50):
    getOptimal(dis)